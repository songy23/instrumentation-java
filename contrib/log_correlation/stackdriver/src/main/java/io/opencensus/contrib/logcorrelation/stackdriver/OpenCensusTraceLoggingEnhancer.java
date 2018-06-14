/*
 * Copyright 2018, OpenCensus Authors
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

package io.opencensus.contrib.logcorrelation.stackdriver;

import com.google.cloud.ServiceOptions;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingEnhancer;
import io.opencensus.common.ExperimentalApi;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.TraceId;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import java.util.logging.LogManager;

/**
 * Stackdriver {@link LoggingEnhancer} that adds OpenCensus tracing data to log entries.
 *
 * <p>This feature is currently experimental.
 */
@ExperimentalApi
public final class OpenCensusTraceLoggingEnhancer implements LoggingEnhancer {
  private static final String SAMPLED_LABEL_KEY = "sampled";
  private static final SpanSelection DEFAULT_SPAN_SELECTION = SpanSelection.ALL_SPANS;

  /** Name of the property that defines the {@link SpanSelection}. The value is {@value}. */
  public static final String SPAN_SELECTION_PROPERTY_NAME =
      "io.opencensus.contrib.logcorrelation.stackdriver."
          + "OpenCensusTraceLoggingEnhancer.spanSelection";

  private static final Tracer tracer = Tracing.getTracer();

  private final SpanSelection spanSelection;

  /** How to decide whether to add tracing data from the current span to a log entry. */
  public enum SpanSelection {

    /**
     * Never add tracing data to log entries. This constant disables the log correlation feature.
     */
    NO_SPANS,

    /** Add tracing data to a log entry iff the current span is sampled. */
    SAMPLED_SPANS,

    /**
     * Always add tracing data to log entries, even when the current span is not sampled. This is
     * the default.
     */
    ALL_SPANS
  }

  /**
   * Constructor to be called by Stackdriver logging.
   *
   * <p>This constructor looks up the {@link SpanSelection} from the environment using the property
   * name {@value SPAN_SELECTION_PROPERTY_NAME}. It looks for a {@link java.util.logging} property
   * or a system property, giving preference to the logging property.
   */
  public OpenCensusTraceLoggingEnhancer() {
    this(lookUpSpanSelectionProperty());
  }

  /** Constructor used for testing. */
  OpenCensusTraceLoggingEnhancer(SpanSelection spanSelection) {
    this.spanSelection = spanSelection;
  }

  private static SpanSelection lookUpSpanSelectionProperty() {
    String spanSelectionProperty = lookUpProperty(SPAN_SELECTION_PROPERTY_NAME);
    return spanSelectionProperty == null || spanSelectionProperty.isEmpty()
        ? DEFAULT_SPAN_SELECTION
        : parseSpanSelection(spanSelectionProperty);
  }

  private static SpanSelection parseSpanSelection(String spanSelection) {
    try {
      return SpanSelection.valueOf(spanSelection);
    } catch (IllegalArgumentException e) {
      return DEFAULT_SPAN_SELECTION;
    }
  }

  // An OpenCensusTraceLoggingEnhancer property can be set with a logging property or a system
  // property.
  private static String lookUpProperty(String name) {
    String property = LogManager.getLogManager().getProperty(name);
    return property == null || property.isEmpty() ? System.getProperty(name) : property;
  }

  /**
   * Returns the {@code SpanSelection} setting for this instance.
   *
   * @return the {@code SpanSelection} setting for this instance.
   */
  public SpanSelection getSpanSelection() {
    return spanSelection;
  }

  // This method avoids getting the current span when the feature is disabled, for efficiency.
  @Override
  public void enhanceLogEntry(LogEntry.Builder builder) {
    switch (spanSelection) {
      case NO_SPANS:
        return;
      case SAMPLED_SPANS:
        SpanContext span = tracer.getCurrentSpan().getContext();
        if (span.getTraceOptions().isSampled()) {
          addTracingData(span, builder);
        }
        return;
      case ALL_SPANS:
        addTracingData(tracer.getCurrentSpan().getContext(), builder);
        return;
    }
    throw new AssertionError("Unknown spanSelection: " + spanSelection);
  }

  private static void addTracingData(SpanContext span, LogEntry.Builder builder) {
    builder.setTrace(formatTraceId(span.getTraceId()));
    builder.setSpanId(span.getSpanId().toLowerBase16());

    // TODO(sebright): Find the correct way to add the sampling decision.
    builder.addLabel(SAMPLED_LABEL_KEY, Boolean.toString(span.getTraceOptions().isSampled()));
  }

  private static String formatTraceId(TraceId traceId) {
    // TODO(sebright): Cache the project ID.
    // TODO(sebright): Add a way to override the project ID in the logging configuration.
    String projectId = ServiceOptions.getDefaultProjectId();
    return "projects/" + projectId + "/traces/" + traceId.toLowerBase16();
  }
}
