/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.multidevice.ViewOnceOpenMessage;

@Deprecated(1641027661)
public class JsonViewOnceOpenMessage {
  public JsonAddress sender;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;

  public JsonViewOnceOpenMessage(ViewOnceOpenMessage message) {
    sender = new JsonAddress(message.getSender());
    timestamp = message.getTimestamp();
  }
}
