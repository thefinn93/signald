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
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@ProtocolType("remote_delete")
@Doc("delete a message previously sent")
public class RemoteDeleteRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("the account to use") @Required public String account;

  @ExactlyOneOfRequired(RECIPIENT)
  @Doc("the address to send the delete message to. should match address the message to be deleted was sent to. required if group is not set.")
  public JsonAddress address;

  @ExampleValue(ExampleValue.GROUP_ID)
  @Doc("the group to send the delete message to. should match group the message to be deleted was sent to. required if address is not set.")
  @ExactlyOneOfRequired(RECIPIENT)
  public String group;

  @Required public long timestamp;

  @Doc("Optionally set to a sub-set of group members. Ignored if group isn't specified") public List<JsonAddress> members;

  @Override
  public SendResponse run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRecipientError, UnknownGroupError,
                                                  InvalidRequestError, RateLimitError, UnregisteredUserError, AuthorizationFailedError, SQLError, ProofRequiredError {
    Account a = Common.getAccount(account);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withRemoteDelete(new SignalServiceDataMessage.RemoteDelete(timestamp));
    Recipient recipient = null;
    if (address != null) {
      recipient = Common.getRecipient(Database.Get(a.getACI()).RecipientsTable, address);
    }
    List<SendMessageResult> results = Common.send(a, messageBuilder, recipient, group, members);
    return new SendResponse(results, timestamp);
  }
}
