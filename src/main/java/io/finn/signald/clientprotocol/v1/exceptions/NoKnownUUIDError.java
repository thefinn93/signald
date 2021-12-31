/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class NoKnownUUIDError extends ExceptionWrapper {
  String e164;
  public NoKnownUUIDError(String e164) {
    super("No known UUID for that Signal account");
    this.e164 = e164;
  }
}
