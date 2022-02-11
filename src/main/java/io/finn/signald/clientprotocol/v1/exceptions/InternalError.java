/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.BuildConfig;
import io.finn.signald.annotations.Doc;
import io.sentry.Sentry;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Doc("an internal error in signald has occurred. typically these are things that \"should never happen\" such as issues "
     + "saving to the local disk, but it is also the default error type and may catch some things that should have their "
     + "own error type. If you find tht your code is depending on the exception list for any particular behavior, please "
     + "file an issue so we can pull those errors out to a separate error type: " + BuildConfig.ERROR_REPORTING_URL)
public class InternalError extends ExceptionWrapper {
  private static final Logger logger = LogManager.getLogger();
  public final List<String> exceptions;

  public InternalError(String message, Throwable exception) {
    super(exception);
    exceptions = new ArrayList<>();
    Throwable cause = exception;
    while (cause != null) {
      exceptions.add(cause.getClass().getCanonicalName());
      cause = cause.getCause();
    }
    logger.error(message, exception);
    Sentry.captureException(exception);
  }
}
