/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Doc("an internal error in signald has occured.")
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
    logger.error(message, logger);
  }
}
