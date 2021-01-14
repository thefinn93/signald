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

import io.finn.signald.BuildConfig;
import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@SignaldClientRequest(type = "mark_read", ResponseClass = Empty.class)
public class MarkReadRequest implements RequestType {

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("The address that sent the message being marked as read") @Required public JsonAddress to;

  @ExampleValue("[" + BuildConfig.BUILD_TIMESTAMP + "]") @Doc("List of messages to mark as read") @Required public List<Long> timestamps;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException, UntrustedIdentityException {
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, timestamps, System.currentTimeMillis());
    Manager m = Manager.get(account);
    SignalServiceAddress toAddress = m.getResolver().resolve(to.getSignalServiceAddress());
    SignalServiceMessageSender sender = m.getMessageSender();
    sender.sendReceipt(toAddress, Optional.absent(), message);

    List<ReadMessage> readMessages = new LinkedList<>();
    for (Long ts : timestamps) {
      readMessages.add(new ReadMessage(toAddress, ts));
    }
    sender.sendMessage(SignalServiceSyncMessage.forRead(readMessages), Optional.absent());
    request.reply(null);
  }
}
