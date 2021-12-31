/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class InvalidBase64Error extends ExceptionWrapper {
  public InvalidBase64Error() { super("could not decode base64 encoded data in request"); }
}
