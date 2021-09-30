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

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.util.Base64;

@ProtocolType("set_expiration")
@Doc("Set the message expiration timer for a thread. Expiration must be specified in seconds, set to 0 to disable timer")
public class SetExpirationRequest implements RequestType<SendResponse> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("604800") @Required public int expiration;

  @Override
  public SendResponse run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UnknownGroupError, GroupVerificationError, InvalidRequestError {
    List<SendMessageResult> results;

    if (group != null) {
      if (group.length() == 44) {
        Account a = Common.getAccount(account);
        GroupsTable.Group storedGroup = Common.getGroup(a, group);
        GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, storedGroup);
        results = Common.updateGroup(a, storedGroup, groupOperations.createModifyGroupTimerChange(expiration));
      } else {
        logger.warn("v1 group support is being removed https://gitlab.com/signald/signald/-/issues/224");
        byte[] groupId;
        try {
          groupId = Base64.decode(group);
        } catch (IOException e) {
          throw new UnknownGroupError();
        }
        try {
          results = Common.getManager(account).setExpiration(groupId, expiration);
        } catch (IOException | SQLException e) {
          throw new InternalError("error setting expiration", e);
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
          throw new UnknownGroupError();
        }
      }
    } else {
      Manager m = Common.getManager(account);
      Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);
      try {
        results = m.setExpiration(recipient, expiration);
      } catch (IOException | SQLException e) {
        throw new InternalError("error setting expiration", e);
      }
    }

    return new SendResponse(results, 0);
  }
}
