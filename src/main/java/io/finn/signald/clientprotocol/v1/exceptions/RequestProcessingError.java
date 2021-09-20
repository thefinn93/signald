/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RequestProcessingError extends ExceptionWrapper {
  public RequestProcessingError(Throwable throwable) { super(throwable.getMessage(), throwable); }

  @JsonIgnore
  public String getType() {
    return this.getCause().getClass().getSimpleName();
  }
}
