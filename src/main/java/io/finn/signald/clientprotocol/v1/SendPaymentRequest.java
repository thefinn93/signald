/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Account;
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

@ProtocolType("send_payment")
@Doc("send a mobilecoin payment")
public class SendPaymentRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("the account to use") @Required public String account;

  @Required @Doc("the address to send the payment message to") public JsonAddress address;

  @Required public Payment payment;

  public Long when;

  @Override
  public SendResponse run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidBase64Error, InvalidRecipientError,
                                                  UnknownGroupError, InvalidRequestError, RateLimitError, UnregisteredUserError, AuthorizationFailedError, SQLError {
    Account a = Common.getAccount(account);

    Recipient recipient = Common.getRecipient(a.getACI(), address);

    if (when == null) {
      when = System.currentTimeMillis();
    }

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withPayment(new SignalServiceDataMessage.Payment(payment.getPaymentNotification()));
    messageBuilder.withTimestamp(when);

    List<SendMessageResult> results = Common.send(a, messageBuilder, recipient, null, null);
    return new SendResponse(results, when);
  }
}
