/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.util.Base64;

public class ProtocolInvalidKeyIdError extends ExceptionWrapper {
  public final String sender;
  @JsonProperty("sender_device") public final int senderDevice;
  @JsonProperty("content_hint") public int contentHint;
  @JsonProperty("group_id") public String groupId;
  public long timestamp;

  public ProtocolInvalidKeyIdError(SignalServiceEnvelope envelope, ProtocolInvalidKeyIdException e) {
    super(e);
    sender = e.getSender();
    senderDevice = e.getSenderDevice();
    contentHint = e.getContentHint();
    if (e.getGroupId().isPresent()) {
      groupId = Base64.encodeBytes(e.getGroupId().get());
    }
    timestamp = envelope.getTimestamp();
  }
}
