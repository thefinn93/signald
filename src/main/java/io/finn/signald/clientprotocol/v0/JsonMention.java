/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.ACI;

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
    uuid = m.getAci().toString();
    start = m.getStart();
    length = m.getLength();
  }

  public SignalServiceDataMessage.Mention asMention() { return new SignalServiceDataMessage.Mention(ACI.from(UUID.fromString(uuid)), start, length); }
}
