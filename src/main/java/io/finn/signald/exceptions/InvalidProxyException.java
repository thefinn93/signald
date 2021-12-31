/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

public class InvalidProxyException extends Exception {
  public InvalidProxyException(String proxy) { super("invalid proxy: " + (proxy == null ? "null" : proxy)); }
}
