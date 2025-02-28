/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-1.4";

  private static final Instrumenter<JedisRequest, Void> INSTRUMENTER;

  static {
    DbAttributesExtractor<JedisRequest, Void> attributesExtractor =
        new JedisDbAttributesExtractor();
    SpanNameExtractor<JedisRequest> spanName = DbSpanNameExtractor.create(attributesExtractor);
    JedisNetAttributesGetter netAttributesGetter = new JedisNetAttributesGetter();
    NetClientAttributesExtractor<JedisRequest, Void> netAttributesExtractor =
        NetClientAttributesExtractor.create(netAttributesGetter);

    INSTRUMENTER =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanName)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JedisSingletons() {}
}
