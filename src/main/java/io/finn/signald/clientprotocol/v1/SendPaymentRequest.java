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
import io.finn.signald.exceptions.NoSendPermissionException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@ProtocolType("send_payment")
@Doc("send a mobilecoin payment")
public class SendPaymentRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("the account to use") @Required public String account;

  @Required @Doc("the address to send the payment message to") public JsonAddress address;

  @Required public Payment payment;

  public Long when;

  @Override
  public SendResponse run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidBase64Error, InvalidRecipientError, UnknownGroupError, NoSendPermissionError {
    Manager m = Common.getManager(account);

    Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);

    if (when == null) {
      when = System.currentTimeMillis();
    }

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withPayment(new SignalServiceDataMessage.Payment(payment.getPaymentNotification()));
    messageBuilder.withTimestamp(when);

    List<SendMessageResult> results;
    try {
      results = m.send(messageBuilder, recipient, null);
    } catch (io.finn.signald.exceptions.InvalidRecipientException e) {
      throw new InvalidRecipientError();
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (NoSendPermissionException e) {
      throw new NoSendPermissionError();
    } catch (SQLException | IOException e) {
      throw new InternalError("error sending message", e);
    }
    return new SendResponse(results, when);
  }
}
