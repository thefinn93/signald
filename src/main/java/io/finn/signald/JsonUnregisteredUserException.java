/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;
import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

@Deprecated(1641027661)
class JsonUnregisteredUserException {
  public String number;

  JsonUnregisteredUserException(UnregisteredUserException e) { this.number = e.getE164Number(); }
}
