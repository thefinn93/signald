/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;
import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

@Deprecated(1641027661)
class JsonNetworkFailureException {
  public String number;

  JsonNetworkFailureException(NetworkFailureException e) { this.number = e.getE164number(); }
}
