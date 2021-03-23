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
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.InvalidRecipientException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.UnknownGroupException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Doc("reset a session with a particular user")
@SignaldClientRequest(type = "reset_session")
public class ResetSessionRequest implements RequestType<SendResponse> {
  @Required public String account;

  @Required public JsonAddress address;

  public Long timestamp;

  @Override
  public SendResponse run(Request request)
      throws SQLException, IOException, NoSuchAccountException, UnknownGroupException, InvalidRecipientException, GroupNotFoundException, NotAGroupMemberException {
    Manager m = Manager.get(account);
    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis();
    }
    messageBuilder.withTimestamp(timestamp);
    List<SendMessageResult> results = m.send(messageBuilder, address, null);
    return new SendResponse(results, timestamp);
  }
}
