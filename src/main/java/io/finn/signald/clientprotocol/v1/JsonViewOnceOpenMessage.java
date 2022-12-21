/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;

public class JsonViewOnceOpenMessage {
  public JsonAddress sender;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;

  private JsonViewOnceOpenMessage() {}

  public JsonViewOnceOpenMessage(ViewOnceOpenMessage message) {
    sender = new JsonAddress(message.getSender());
    timestamp = message.getTimestamp();
  }

  public ViewOnceOpenMessage toLibSignalClass() { return new ViewOnceOpenMessage(sender.getServiceID(), timestamp); }
}
