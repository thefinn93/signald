/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class InvalidProxyError extends ExceptionWrapper {
  public InvalidProxyError(io.finn.signald.exceptions.InvalidProxyException e) { super(e.getMessage()); }
}
