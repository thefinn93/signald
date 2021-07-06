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

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

@ProtocolType("mark_read")
public class MarkReadRequest implements RequestType<Empty> {

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("The address that sent the message being marked as read") @Required public JsonAddress to;

  @ExampleValue(ExampleValue.MESSAGE_ID) @Doc("List of messages to mark as read") @Required public List<Long> timestamps;

  public Long when;

  @Override
  public Empty run(Request request)
      throws IOException, UntrustedIdentityException, SQLException, NoSuchAccount, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    if (when == null) {
      when = System.currentTimeMillis();
    }
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, timestamps, when);
    Manager m = Utils.getManager(account);
    SignalServiceAddress toAddress = m.getResolver().resolve(to.getSignalServiceAddress());
    SignalServiceMessageSender sender = m.getMessageSender();
    sender.sendReceipt(toAddress, m.getAccessPairFor(toAddress), message);

    List<ReadMessage> readMessages = new LinkedList<>();
    for (Long ts : timestamps) {
      readMessages.add(new ReadMessage(toAddress, ts));
    }
    sender.sendSyncMessage(SignalServiceSyncMessage.forRead(readMessages), m.getAccessPairFor(m.getOwnAddress()));
    return new Empty();
  }
}
