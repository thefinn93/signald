/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.util.UUID;

public class JsonMention {
  @Doc("The UUID of the account being mentioned") public String uuid;
  @Doc(
      "The position in the message that the mention is, by character count. Note that due to a quirk of how signald encodes JSON, if this value is 0 (for example if the first character in the message is the mention) the field won't show up.")
  public int start;
  @Doc("The length of the mention represented in the message. Seems to always be 1 but included here in case that changes. Please open an issu") public int length;

  public JsonMention() {}

  public JsonMention(SignalServiceDataMessage.Mention m) {
    uuid = m.getUuid().toString();
    start = m.getStart();
    length = m.getLength();
  }

  public SignalServiceDataMessage.Mention asMention() { return new SignalServiceDataMessage.Mention(UUID.fromString(uuid), start, length); }
}
