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
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.InvalidRecipientException;
import io.finn.signald.exceptions.UnknownGroupException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.InvalidKeyException;
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

  @Override
  public SendResponse run(Request request) throws IOException, GroupNotFoundException, NotAGroupMemberException, InvalidRecipientException, InvalidInputException,
                                                  UnknownGroupException, SQLException, NoSuchAccount, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Utils.getManager(username);
    if (timestamp > 0) {
      timestamp = System.currentTimeMillis();
    }

    reaction.resolve(m.getResolver());

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withReaction(reaction.getReaction());
    List<SendMessageResult> results = null;
    try {
      results = m.send(messageBuilder, recipientAddress, recipientGroupId);
    } catch (io.finn.signald.exceptions.InvalidRecipientException e) {
      throw new InvalidRecipientException();
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupException();
    }
    return new SendResponse(results, timestamp);
  }
}
