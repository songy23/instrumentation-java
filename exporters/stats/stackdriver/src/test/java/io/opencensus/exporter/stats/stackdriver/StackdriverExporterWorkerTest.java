/*
 * Copyright 2017, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opencensus.exporter.stats.stackdriver;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.stub.MetricServiceStub;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.monitoring.v3.CreateMetricDescriptorRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.TimeSeries;
import com.google.protobuf.Empty;
import io.opencensus.common.Duration;
import io.opencensus.common.Timestamp;
import io.opencensus.stats.Aggregation.Sum;
import io.opencensus.stats.AggregationData;
import io.opencensus.stats.AggregationData.SumDataLong;
import io.opencensus.stats.Measure.MeasureLong;
import io.opencensus.stats.View;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewData;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit tests for {@link StackdriverExporterWorker}. */
@RunWith(JUnit4.class)
public class StackdriverExporterWorkerTest {

  private static final String PROJECT_ID = "projectId";
  private static final Duration ONE_SECOND = Duration.create(1, 0);
  private static final TagKey KEY = TagKey.create("KEY");
  private static final TagValue VALUE = TagValue.create("VALUE");
  private static final String MEASURE_NAME = "my measurement";
  private static final String MEASURE_UNIT = "us";
  private static final String MEASURE_DESCRIPTION = "measure description";
  private static final MeasureLong MEASURE =
      MeasureLong.create(MEASURE_NAME, MEASURE_DESCRIPTION, MEASURE_UNIT);
  private static final Name VIEW_NAME = Name.create("my view");
  private static final String VIEW_DESCRIPTION = "view description";
  private static final Sum SUM = Sum.create();
  private static final MonitoredResource DEFAULT_RESOURCE =
      MonitoredResource.newBuilder().setType("global").build();

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Mock private ViewManager mockViewManager;

  @Mock private MetricServiceStub mockStub;

  @Mock
  private UnaryCallable<CreateMetricDescriptorRequest, MetricDescriptor>
      mockCreateMetricDescriptorCallable;

  @Mock private UnaryCallable<CreateTimeSeriesRequest, Empty> mockCreateTimeSeriesCallable;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    doReturn(mockCreateMetricDescriptorCallable).when(mockStub).createMetricDescriptorCallable();
    doReturn(mockCreateTimeSeriesCallable).when(mockStub).createTimeSeriesCallable();
    doReturn(null)
        .when(mockCreateMetricDescriptorCallable)
        .call(any(CreateMetricDescriptorRequest.class));
    doReturn(null).when(mockCreateTimeSeriesCallable).call(any(CreateTimeSeriesRequest.class));
  }

  @Test
  public void testConstants() {
    assertThat(StackdriverExporterWorker.MAX_BATCH_EXPORT_SIZE).isEqualTo(200);
  }

  @Test
  public void export() throws IOException {
    View view = View.create(VIEW_NAME, VIEW_DESCRIPTION, MEASURE, SUM, Arrays.asList(KEY));
    ViewData viewData =
        ViewData.create(
            view,
            ImmutableMap.of(Arrays.asList(VALUE), SumDataLong.create(1)),
            Timestamp.fromMillis(100),
            Timestamp.fromMillis(200));
    doReturn(ImmutableSet.of(view)).when(mockViewManager).getAllExportedViews();
    doReturn(viewData).when(mockViewManager).getView(VIEW_NAME);

    StackdriverExporterWorker worker =
        new StackdriverExporterWorker(
            PROJECT_ID,
            new FakeMetricServiceClient(mockStub),
            ONE_SECOND,
            mockViewManager,
            DEFAULT_RESOURCE);
    worker.export();

    verify(mockStub, times(1)).createMetricDescriptorCallable();
    verify(mockStub, times(1)).createTimeSeriesCallable();

    MetricDescriptor descriptor = StackdriverExportUtils.createMetricDescriptor(view, PROJECT_ID);
    List<TimeSeries> timeSeries =
        StackdriverExportUtils.createTimeSeriesList(viewData, DEFAULT_RESOURCE);
    verify(mockCreateMetricDescriptorCallable, times(1))
        .call(
            eq(
                CreateMetricDescriptorRequest.newBuilder()
                    .setName("projects/" + PROJECT_ID)
                    .setMetricDescriptor(descriptor)
                    .build()));
    verify(mockCreateTimeSeriesCallable, times(1))
        .call(
            eq(
                CreateTimeSeriesRequest.newBuilder()
                    .setName("projects/" + PROJECT_ID)
                    .addAllTimeSeries(timeSeries)
                    .build()));
  }

  @Test
  public void doNotExportForEmptyViewData() {
    View view = View.create(VIEW_NAME, VIEW_DESCRIPTION, MEASURE, SUM, Arrays.asList(KEY));
    ViewData empty =
        ViewData.create(
            view,
            Collections.<List<TagValue>, AggregationData>emptyMap(),
            Timestamp.fromMillis(100),
            Timestamp.fromMillis(200));
    doReturn(ImmutableSet.of(view)).when(mockViewManager).getAllExportedViews();
    doReturn(empty).when(mockViewManager).getView(VIEW_NAME);

    StackdriverExporterWorker worker =
        new StackdriverExporterWorker(
            PROJECT_ID,
            new FakeMetricServiceClient(mockStub),
            ONE_SECOND,
            mockViewManager,
            DEFAULT_RESOURCE);

    worker.export();
    verify(mockStub, times(1)).createMetricDescriptorCallable();
    verify(mockStub, times(0)).createTimeSeriesCallable();
  }

  @Test
  public void doNotExportIfFailedToRegisterView() {
    View view = View.create(VIEW_NAME, VIEW_DESCRIPTION, MEASURE, SUM, Arrays.asList(KEY));
    doReturn(ImmutableSet.of(view)).when(mockViewManager).getAllExportedViews();
    doThrow(new IllegalArgumentException()).when(mockStub).createMetricDescriptorCallable();
    StackdriverExporterWorker worker =
        new StackdriverExporterWorker(
            PROJECT_ID,
            new FakeMetricServiceClient(mockStub),
            ONE_SECOND,
            mockViewManager,
            DEFAULT_RESOURCE);

    assertThat(worker.registerView(view)).isFalse();
    worker.export();
    verify(mockStub, times(1)).createMetricDescriptorCallable();
    verify(mockStub, times(0)).createTimeSeriesCallable();
  }

  @Test
  public void skipDifferentViewWithSameName() throws IOException {
    StackdriverExporterWorker worker =
        new StackdriverExporterWorker(
            PROJECT_ID,
            new FakeMetricServiceClient(mockStub),
            ONE_SECOND,
            mockViewManager,
            DEFAULT_RESOURCE);
    View view1 = View.create(VIEW_NAME, VIEW_DESCRIPTION, MEASURE, SUM, Arrays.asList(KEY));
    assertThat(worker.registerView(view1)).isTrue();
    verify(mockStub, times(1)).createMetricDescriptorCallable();

    View view2 =
        View.create(
            VIEW_NAME, "This is a different description.", MEASURE, SUM, Arrays.asList(KEY));
    assertThat(worker.registerView(view2)).isFalse();
    verify(mockStub, times(1)).createMetricDescriptorCallable();
  }

  @Test
  public void doNotCreateMetricDescriptorForRegisteredView() {
    StackdriverExporterWorker worker =
        new StackdriverExporterWorker(
            PROJECT_ID,
            new FakeMetricServiceClient(mockStub),
            ONE_SECOND,
            mockViewManager,
            DEFAULT_RESOURCE);
    View view = View.create(VIEW_NAME, VIEW_DESCRIPTION, MEASURE, SUM, Arrays.asList(KEY));
    assertThat(worker.registerView(view)).isTrue();
    verify(mockStub, times(1)).createMetricDescriptorCallable();

    assertThat(worker.registerView(view)).isTrue();
    verify(mockStub, times(1)).createMetricDescriptorCallable();
  }

  /*
   * MetricServiceClient.createMetricDescriptor() and MetricServiceClient.createTimeSeries() are
   * final methods and cannot be mocked. We have to use a mock MetricServiceStub in order to verify
   * the output.
   */
  private static final class FakeMetricServiceClient extends MetricServiceClient {

    protected FakeMetricServiceClient(MetricServiceStub stub) {
      super(stub);
    }
  }
}
