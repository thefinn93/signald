/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Account;
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
  public SendResponse run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, InvalidRecipientError, UnknownGroupError,
                                                  InvalidRequestError, RateLimitError, UnregisteredUserError, AuthorizationFailedError, SQLError {
    Account a = Common.getAccount(username);

    if (timestamp > 0) {
      timestamp = System.currentTimeMillis();
    }
    Recipient recipient = null;
    if (recipientAddress != null) {
      recipient = Common.getRecipient(a.getACI(), recipientAddress);
    }

    Recipient reactionTarget = Common.getRecipient(a.getACI(), reaction.targetAuthor);
    reaction.targetAuthor = new JsonAddress(reactionTarget);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withReaction(reaction.getReaction());
    List<SendMessageResult> results = Common.send(a, messageBuilder, recipient, recipientGroupId, members);
    return new SendResponse(results, timestamp);
  }
}
