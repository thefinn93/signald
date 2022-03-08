/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Doc("reset a session with a particular user")
@ProtocolType("reset_session")
public class ResetSessionRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @Doc("the user to reset session with") @Required public JsonAddress address;

  public Long timestamp;

  @Override
  public SendResponse run(Request request) throws InternalError, ServerNotFoundError, InvalidProxyError, NoSuchAccountError, InvalidRequestError, NoSendPermissionError,
                                                  UnknownGroupError, RateLimitError, InvalidRecipientError, UnregisteredUserError, AuthorizationFailedError, SQLError {
    Manager m = Common.getManager(account);
    Recipient recipient = Common.getRecipient(m.getACI(), address);
    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis();
    }
    messageBuilder.withTimestamp(timestamp);
    List<SendMessageResult> results = Common.send(m, messageBuilder, recipient, null, null);
    return new SendResponse(results, timestamp);
  }
}
