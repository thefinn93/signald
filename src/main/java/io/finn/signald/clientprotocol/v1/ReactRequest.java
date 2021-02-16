/*
 * Copyright (C) 2020 Finn Herzfeld
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
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.InvalidRecipientException;
import io.finn.signald.exceptions.UnknownGroupException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

@SignaldClientRequest(type = "react", ResponseClass = SendResponse.class)
@Doc("react to a previous message")
public class ReactRequest implements RequestType {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Required public String username;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress recipientAddress;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String recipientGroupId;
  @Required public JsonReaction reaction;
  public long timestamp;

  @Override
  public void run(Request request) throws IOException, GroupNotFoundException, NotAGroupMemberException, InvalidRecipientException, NoSuchAccountException, InvalidInputException,
                                          UnknownGroupException, SQLException {
    Manager m = Manager.get(username);

    if (timestamp > 0) {
      timestamp = System.currentTimeMillis();
    }

    reaction.resolve(m.getResolver());

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withReaction(reaction.getReaction());
    List<SendMessageResult> results = m.send(messageBuilder, recipientAddress, recipientGroupId);
    request.reply(new SendResponse(results, timestamp));
  }
}
