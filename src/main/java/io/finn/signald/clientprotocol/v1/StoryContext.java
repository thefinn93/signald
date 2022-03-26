/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class StoryContext {
  public String author;
  @JsonProperty("sent_timestamp") public long sentTimestamp;

  public StoryContext(SignalServiceDataMessage.StoryContext s) {
    author = s.getAuthorServiceId().toString();
    sentTimestamp = s.getSentTimestamp();
  }
}
