/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonTypingMessage {
  public String action;
  public long timestamp;
  public String groupId;

  public JsonTypingMessage(SignalServiceTypingMessage typingMessage) {
    action = typingMessage.getAction().name();
    timestamp = typingMessage.getTimestamp();
    if (typingMessage.getGroupId().isPresent()) {
      groupId = Base64.encodeBytes(typingMessage.getGroupId().get());
    }
  }
}
