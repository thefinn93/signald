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
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@Doc("reset a session with a particular user")
@ProtocolType("reset_session")
public class ResetSessionRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @Doc("the user to reset session with") @Required public JsonAddress address;

  public Long timestamp;

  @Override
  public SendResponse run(Request request) throws SQLException, IOException, NoSuchAccount, UnknownGroupException, InvalidRecipientException, GroupNotFoundException,
                                                  NotAGroupMemberException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Utils.getManager(account);
    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();
    if (timestamp == null) {
      timestamp = System.currentTimeMillis();
    }
    messageBuilder.withTimestamp(timestamp);
    List<SendMessageResult> results = null;
    try {
      results = m.send(messageBuilder, address, null);
    } catch (io.finn.signald.exceptions.InvalidRecipientException e) {
      throw new InvalidRecipientException();
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupException();
    }
    return new SendResponse(results, timestamp);
  }
}
