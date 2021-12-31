/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

public class JsonReaction {

  @ExampleValue("\"👍\"") @Doc("the emoji to react with") public String emoji;

  @Doc("set to true to remove the reaction. requires emoji be set to previously reacted emoji") public boolean remove;

  @Doc("the author of the message being reacted to") public JsonAddress targetAuthor;

  @ExampleValue(ExampleValue.MESSAGE_ID) @Doc("the client timestamp of the message being reacted to") public long targetSentTimestamp;

  public JsonReaction() {}

  public JsonReaction(SignalServiceDataMessage.Reaction r) {
    emoji = r.getEmoji();
    remove = r.isRemove();
    targetAuthor = new JsonAddress(r.getTargetAuthor());
    targetSentTimestamp = r.getTargetSentTimestamp();
  }

  @JsonIgnore
  public SignalServiceDataMessage.Reaction getReaction() {
    return new SignalServiceDataMessage.Reaction(emoji, remove, targetAuthor.getSignalServiceAddress(), targetSentTimestamp);
  }
}
