/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;

public class JsonReadMessage {
  public JsonAddress sender;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;

  public JsonReadMessage(ReadMessage r) {
    sender = new JsonAddress(r.getSender());
    timestamp = r.getTimestamp();
  }
}
