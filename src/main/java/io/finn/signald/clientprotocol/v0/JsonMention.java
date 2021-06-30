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

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Deprecated(1641027661)
public class JsonMention {
  @ExampleValue(ExampleValue.REMOTE_UUID) @Doc("The UUID of the account being mentioned") public String uuid;

  @Doc("The number of characters in that the mention starts at. Note that due to a quirk of how signald encodes JSON, if "
       + "this value is 0 (for example if the first character in the message is the mention) the field won't show up.")
  @ExampleValue("4") // make sure this lines up with the mention in JsonQuote
  public int start;

  @ExampleValue("1") @Doc("The length of the mention represented in the message. Seems to always be 1 but included here in case that changes.") public int length;

  public JsonMention() {}

  public JsonMention(SignalServiceDataMessage.Mention m) {
    uuid = m.getUuid().toString();
    start = m.getStart();
    length = m.getLength();
  }

  public SignalServiceDataMessage.Mention asMention() { return new SignalServiceDataMessage.Mention(UUID.fromString(uuid), start, length); }
}
