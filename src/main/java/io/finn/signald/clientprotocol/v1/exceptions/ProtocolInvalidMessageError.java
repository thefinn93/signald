/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.whispersystems.util.Base64;

public class ProtocolInvalidMessageError extends ExceptionWrapper {
  public final String sender;
  @JsonProperty("sender_device") public final int senderDevice;
  @JsonProperty("content_hint") public int contentHint;
  @JsonProperty("group_id") public String groupId;

  public ProtocolInvalidMessageError(ProtocolInvalidMessageException e) {
    super(e);
    sender = e.getSender();
    senderDevice = e.getSenderDevice();
    contentHint = e.getContentHint();
    if (e.getGroupId().isPresent()) {
      groupId = Base64.encodeBytes(e.getGroupId().get());
    }
  }
}
