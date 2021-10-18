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

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@ProtocolType("react")
@Doc("react to a previous message")
public class ReactRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Required public String username;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress recipientAddress;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String recipientGroupId;
  @Required public JsonReaction reaction;
  public long timestamp;
  @Doc("Optionally set to a sub-set of group members. Ignored if recipientGroupId isn't specified") public List<JsonAddress> members;

  @Override
  public SendResponse run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, NoSendPermissionError, InternalError, InvalidRecipientError,
                                                  UnknownGroupError, InvalidRequestError, RateLimitError {
    Manager m = Common.getManager(username);
    if (timestamp > 0) {
      timestamp = System.currentTimeMillis();
    }
    Recipient recipient = null;
    if (recipientAddress != null) {
      recipient = Common.getRecipient(m.getRecipientsTable(), recipientAddress);
    }

    Recipient reactionTarget = Common.getRecipient(m.getRecipientsTable(), reaction.targetAuthor);
    reaction.targetAuthor = new JsonAddress(reactionTarget);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withReaction(reaction.getReaction());
    List<SendMessageResult> results = Common.send(m, messageBuilder, recipient, recipientGroupId, members);
    return new SendResponse(results, timestamp);
  }
}
