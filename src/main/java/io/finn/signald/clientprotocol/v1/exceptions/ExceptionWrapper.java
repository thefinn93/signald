/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.ProtocolIgnore;

/* ExceptionWrapper is an Exception that can be JSON encoded nicely
 */
public class ExceptionWrapper extends Exception {
  private boolean unexpected = false;

  public ExceptionWrapper() {}

  public ExceptionWrapper(String message) { super(message); }

  public ExceptionWrapper(String message, Throwable cause) { super(message, cause); }

  public ExceptionWrapper(Throwable cause) { super(cause); }

  public static ExceptionWrapper fromThrowable(Throwable e) {
    if (e instanceof ExceptionWrapper) {
      return (ExceptionWrapper)e;
    }
    return new ExceptionWrapper(e);
  }

  public void setUnexpected(boolean u) { unexpected = u; }

  @JsonIgnore
  public boolean isUnexpected() {
    return unexpected;
  }

  @JsonIgnore
  public String getType() {
    return this.getClass().getSimpleName();
  }

  @JsonGetter("stackTrace")
  @ProtocolIgnore
  public String getJSONStackTrace() {
    return null;
  }

  @JsonGetter("suppressedExceptions")
  @ProtocolIgnore
  public String getJSONSuppressedExceptions() {
    return null;
  }

  @JsonGetter("suppressed")
  @ProtocolIgnore
  public String getJSONSuppressed() {
    return null;
  }

  @JsonGetter("ourStackTrace")
  @ProtocolIgnore
  public String getJSONOurStackTrace() {
    return null;
  }

  @JsonGetter("detailMessage")
  @ProtocolIgnore
  public String getJSONDetailMessage() {
    return null;
  }

  @JsonGetter("localizedMessage")
  @ProtocolIgnore
  public String getJSONLocalizedMessage() {
    return null;
  }

  @JsonGetter("cause")
  @ProtocolIgnore
  public String getJSONCause() {
    return null;
  }
}
