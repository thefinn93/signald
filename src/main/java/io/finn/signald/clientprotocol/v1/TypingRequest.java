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

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@ProtocolType("typing")
@Doc("send a typing started or stopped message")
public class TypingRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("true") @Required public boolean typing;
  public long when;

  @Override
  public Empty run(Request request)
      throws SQLException, IOException, NoSuchAccount, UntrustedIdentityException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Utils.getManager(account);

    byte[] groupId = null;
    if (group != null) {
      groupId = Base64.decode(group);
    }
    if (groupId == null) {
      groupId = new byte[0];
    }

    if (when == 0) {
      when = System.currentTimeMillis();
    }

    SignalServiceTypingMessage.Action action = typing ? SignalServiceTypingMessage.Action.STARTED : SignalServiceTypingMessage.Action.STOPPED;
    SignalServiceTypingMessage message = new SignalServiceTypingMessage(action, when, Optional.fromNullable(groupId));
    SignalServiceAddress addr = m.getResolver().resolve(address.getSignalServiceAddress());
    SignalServiceMessageSender messageSender = m.getMessageSender();

    try {
      messageSender.sendTyping(addr, m.getAccessPairFor(addr), message);
    } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
      m.getAccountData().axolotlStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
      throw e;
    }
    return new Empty();
  }
}
