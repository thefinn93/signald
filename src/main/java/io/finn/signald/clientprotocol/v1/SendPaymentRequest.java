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
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSendPermission;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidRecipientException;
import io.finn.signald.exceptions.NoSendPermissionException;
import io.finn.signald.exceptions.UnknownGroupException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.libsignal.InvalidKeyException;
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
  public SendResponse run(Request request) throws IOException, SQLException, NoSuchAccount, InvalidRecipientException, UnknownGroupException, InvalidProxyException,
                                                  InvalidKeyException, ServerNotFoundException, NoSendPermission {
    Manager m = Utils.getManager(account);

    Recipient recipient = m.getRecipientsTable().get(address);

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
      throw new InvalidRecipientException();
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupException();
    } catch (NoSendPermissionException e) {
      throw new NoSendPermission();
    }
    return new SendResponse(results, when);
  }
}
