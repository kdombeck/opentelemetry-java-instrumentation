/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpServerMetricsTest {

  @Test
  void collectsMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setMinimumCollectionInterval(Duration.ZERO)
            .build();

    RequestListener listener = HttpServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder()
            .put("http.method", "GET")
            .put("http.host", "host")
            .put("http.target", "/")
            .put("http.scheme", "https")
            .put("net.host.name", "localhost")
            .put("net.host.port", 1234)
            .build();

    Attributes responseAttributes =
        Attributes.builder()
            .put("http.flavor", "2.0")
            .put("http.server_name", "server")
            .put("http.status_code", 200)
            .build();

    Context parent =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        "ff01020304050600ff0a0b0c0d0e0f00",
                        "090a0b0c0d0e0f00",
                        TraceFlags.getSampled(),
                        TraceState.getDefault())));

    Context context1 = listener.start(parent, requestAttributes, nanos(100));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(1)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasDescription(
                        "The number of concurrent HTTP requests that are currently in-flight")
                    .hasUnit("requests")
                    .hasLongSum()
                    .points()
                    .satisfiesExactly(
                        point -> {
                          assertThat(point)
                              .hasValue(1)
                              .attributes()
                              .containsOnly(
                                  attributeEntry("http.host", "host"),
                                  attributeEntry("http.method", "GET"),
                                  attributeEntry("http.scheme", "https"));
                          assertThat(point).exemplars().hasSize(1);
                          assertThat(point.getExemplars().get(0))
                              .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                              .hasSpanId("090a0b0c0d0e0f00");
                        }));

    Context context2 = listener.start(Context.root(), requestAttributes, nanos(150));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(1)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSum()
                    .points()
                    .satisfiesExactly(point -> assertThat(point).hasValue(2)));

    listener.end(context1, responseAttributes, nanos(250));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(2)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSum()
                    .points()
                    .satisfiesExactly(point -> assertThat(point).hasValue(1)))
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.duration")
                    .hasUnit("ms")
                    .hasDoubleHistogram()
                    .points()
                    .satisfiesExactly(
                        point -> {
                          assertThat(point)
                              .hasSum(150 /* millis */)
                              .attributes()
                              .containsOnly(
                                  attributeEntry("http.scheme", "https"),
                                  attributeEntry("http.host", "host"),
                                  attributeEntry("http.method", "GET"),
                                  attributeEntry("http.status_code", 200),
                                  attributeEntry("http.flavor", "2.0"));
                          assertThat(point).exemplars().hasSize(1);
                          assertThat(point.getExemplars().get(0))
                              .hasTraceId("ff01020304050600ff0a0b0c0d0e0f00")
                              .hasSpanId("090a0b0c0d0e0f00");
                        }));

    listener.end(context2, responseAttributes, nanos(300));

    assertThat(metricReader.collectAllMetrics())
        .hasSize(2)
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.active_requests")
                    .hasLongSum()
                    .points()
                    .satisfiesExactly(point -> assertThat(point).hasValue(0)))
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.duration")
                    .hasDoubleHistogram()
                    .points()
                    .satisfiesExactly(point -> assertThat(point).hasSum(300 /* millis */)));
  }

  @Test
  void collectsHttpRouteFromEndAttributes() {
    // given
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(metricReader)
            .setMinimumCollectionInterval(Duration.ZERO)
            .build();

    RequestListener listener = HttpServerMetrics.get().create(meterProvider.get("test"));

    Attributes requestAttributes =
        Attributes.builder().put("http.host", "host").put("http.scheme", "https").build();

    Attributes responseAttributes = Attributes.builder().put("http.route", "/test/{id}").build();

    Context parentContext = Context.root();

    // when
    Context context = listener.start(parentContext, requestAttributes, nanos(100));
    listener.end(context, responseAttributes, nanos(200));

    // then
    assertThat(metricReader.collectAllMetrics())
        .anySatisfy(
            metric ->
                assertThat(metric)
                    .hasName("http.server.duration")
                    .hasUnit("ms")
                    .hasDoubleHistogram()
                    .points()
                    .satisfiesExactly(
                        point ->
                            assertThat(point)
                                .hasSum(100 /* millis */)
                                .attributes()
                                .containsOnly(
                                    attributeEntry("http.scheme", "https"),
                                    attributeEntry("http.host", "host"),
                                    attributeEntry("http.route", "/test/{id}"))));
  }

  private static long nanos(int millis) {
    return TimeUnit.MILLISECONDS.toNanos(millis);
  }
}
