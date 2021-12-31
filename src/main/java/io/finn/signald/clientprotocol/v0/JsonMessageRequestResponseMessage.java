/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonMessageRequestResponseMessage {
  public JsonAddress person;
  public String groupId;
  public String type;

  public JsonMessageRequestResponseMessage(MessageRequestResponseMessage m) {
    if (m.getPerson().isPresent()) {
      person = new JsonAddress(m.getPerson().get());
    }

    if (m.getGroupId().isPresent()) {
      groupId = Base64.encodeBytes(m.getGroupId().get());
    }

    type = m.getType().toString();
  }
}
