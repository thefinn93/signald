/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import org.whispersystems.util.Base64;

public class DecryptionErrorMessage {
  public final long timestamp;
  @JsonProperty("device_id") public final int deviceId;
  @JsonProperty("ratchet_key") public String ratchetKey;

  public DecryptionErrorMessage(org.signal.libsignal.protocol.message.DecryptionErrorMessage message) {
    timestamp = message.getTimestamp();
    deviceId = message.getDeviceId();
    if(message.getRatchetKey().isPresent()) {
      ratchetKey = Base64.encodeBytes(message.getRatchetKey().get().serialize());
    }
  }
}
