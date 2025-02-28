/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import static io.opentelemetry.javaagent.instrumentation.play.v2_4.Play24Singletons.instrumenter;

import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mvc.Result;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

public class RequestCompleteCallback extends AbstractFunction1<Try<Result>, Object> {

  private static final Logger logger = LoggerFactory.getLogger(RequestCompleteCallback.class);

  private final Context context;

  public RequestCompleteCallback(Context context) {
    this.context = context;
  }

  @Override
  public Object apply(Try<Result> result) {
    try {
      instrumenter().end(context, null, null, result.isFailure() ? result.failed().get() : null);
    } catch (Throwable t) {
      logger.debug("error in play instrumentation", t);
    }
    return null;
  }
}
