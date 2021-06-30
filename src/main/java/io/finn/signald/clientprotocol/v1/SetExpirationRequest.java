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
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.UnknownGroupException;
import io.finn.signald.storage.Group;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.util.Base64;

@ProtocolType("set_expiration")
@Doc("Set the message expiration timer for a thread. Expiration must be specified in seconds, set to 0 to disable timer")
public class SetExpirationRequest implements RequestType<SendResponse> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("604800") @Required public int expiration;

  @Override
  public SendResponse run(Request request)
      throws SQLException, IOException, NoSuchAccount, UnknownGroupException, VerificationFailedException, NotAGroupMemberException, GroupNotFoundException {
    Manager m = Utils.getManager(account);
    List<SendMessageResult> results;

    if (group != null) {
      if (group.length() == 44) {
        Pair<SignalServiceDataMessage.Builder, Group> output = null;
        try {
          output = m.getGroupsV2Manager().updateGroupTimer(group, expiration);
          results = m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2());
        } catch (io.finn.signald.exceptions.UnknownGroupException e) {
          throw new UnknownGroupException();
        }
        m.getAccountData().groupsV2.update(output.second());
        m.getAccountData().save();
      } else {
        byte[] groupId = Base64.decode(group);
        results = m.setExpiration(groupId, expiration);
      }
    } else {
      results = m.setExpiration(address.getSignalServiceAddress(), expiration);
    }

    return new SendResponse(results, 0);
  }
}
