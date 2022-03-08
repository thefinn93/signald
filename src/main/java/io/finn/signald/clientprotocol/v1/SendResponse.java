/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

public class SendResponse {
  public List<JsonSendMessageResult> results;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;

  public SendResponse(List<SendMessageResult> r, long t) {
    results = r.stream().map(JsonSendMessageResult::new).collect(Collectors.toList());
    timestamp = t;
  }
}
