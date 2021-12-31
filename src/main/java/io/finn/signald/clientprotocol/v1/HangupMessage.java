/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HangupMessage {
  public final long id;
  public final String type;
  @JsonProperty("device_id") public final int deviceId;
  public final boolean legacy;

  public HangupMessage(org.whispersystems.signalservice.api.messages.calls.HangupMessage message) {
    id = message.getId();
    type = message.getType().getCode();
    deviceId = message.getDeviceId();
    legacy = message.isLegacy();
  }
}
