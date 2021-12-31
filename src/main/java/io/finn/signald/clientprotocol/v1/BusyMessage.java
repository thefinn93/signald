/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

public class BusyMessage {
  public final long id;

  public BusyMessage(org.whispersystems.signalservice.api.messages.calls.BusyMessage message) { id = message.getId(); }
}
