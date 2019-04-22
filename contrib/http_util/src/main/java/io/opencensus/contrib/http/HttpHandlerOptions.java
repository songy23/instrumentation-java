/*
 * Copyright 2019, OpenCensus Authors
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

package io.opencensus.contrib.http;

import com.google.auto.value.AutoValue;
import io.opencensus.trace.Tracer;
import io.opencensus.tags.propagation.TagContextTextFormat;
import io.opencensus.trace.propagation.TextFormat;

/**
 * Options for creating {@link HttpClientHandler} or {@link HttpServerHandler}.
 *
 * @since 0.22
 */
@AutoValue
public abstract class HttpHandlerOptions {

  HttpHandlerOptions() {}

  public abstract Tracer getTracer();

  public abstract TextFormat getTraceTextFormat();

  public abstract TextFormat.Setter getTraceSetter();

  public abstract TextFormat.Getter getTraceGetter();

  public abstract TagContextTextFormat getTagsTextFormat();

  public abstract TagContextTextFormat.Setter getTagsSetter();

  public abstract TagContextTextFormat.Getter getTagsGetter();


  /**
   * Returns a new {@link Builder} with default options.
   *
   * @return a new {@code Builder} with default options.
   * @since 0.22
   */
  public abstract Builder builder();

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {}
  }
}
