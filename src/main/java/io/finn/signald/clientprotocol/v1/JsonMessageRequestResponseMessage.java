/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import java.io.IOException;
import org.whispersystems.signalservice.api.messages.multidevice.MessageRequestResponseMessage;
import org.whispersystems.util.Base64;

@Doc("Responses to message requests from unknown users or groups")
public class JsonMessageRequestResponseMessage {
  public JsonAddress person;
  public String groupId;
  @Doc("One of UNKNOWN, ACCEPT, DELETE, BLOCK, BLOCK_AND_DELETE, UNBLOCK_AND_ACCEPT") public String type;

  private JsonMessageRequestResponseMessage() {}

  public JsonMessageRequestResponseMessage(MessageRequestResponseMessage m) {
    if (m.getPerson().isPresent()) {
      person = new JsonAddress(m.getPerson().get());
    }

    if (m.getGroupId().isPresent()) {
      groupId = Base64.encodeBytes(m.getGroupId().get());
    }

    type = m.getType().toString();
  }

  public MessageRequestResponseMessage toLibSignalClass() throws IOException {
    final MessageRequestResponseMessage.Type responseType;
    try {
      responseType = MessageRequestResponseMessage.Type.valueOf(type);
    } catch (IllegalArgumentException e) {
      throw new IOException("unknown message request response type " + type, e);
    }
    if (person != null) {
      return MessageRequestResponseMessage.forIndividual(person.getSignalServiceAddress(), responseType);
    } else if (groupId != null) {
      return MessageRequestResponseMessage.forGroup(Base64.decode(groupId), responseType);
    } else {
      throw new IOException("invalid JsonMessageRequestResponseMessage: person or groupId is required");
    }
  }
}
