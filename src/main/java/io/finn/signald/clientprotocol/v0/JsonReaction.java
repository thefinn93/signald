/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.clientprotocol.v0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Deprecated(1641027661)
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
