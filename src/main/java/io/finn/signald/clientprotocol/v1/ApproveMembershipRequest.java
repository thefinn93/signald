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

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.signal.storageservice.protos.groups.GroupChange;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("approve_membership")
@Doc("approve a request to join a group")
public class ApproveMembershipRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Required @Doc("list of requesting members to approve") public List<JsonAddress> members;

  @Override
  public JsonGroupV2Info run(Request request)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, InternalError, GroupVerificationError, InvalidRequestError {
    Manager m = Common.getManager(account);
    Account a = Common.getAccount(account);

    GroupsTable.Group group = Common.getGroup(a, groupID);

    RecipientsTable recipientsTable = m.getRecipientsTable();
    List<Recipient> recipients;
    try {
      recipients = group.getMembers();
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipients", e);
    }

    for (JsonAddress member : members) {
      try {
        recipients.add(recipientsTable.get(member));
      } catch (SQLException | IOException e) {
        throw new InternalError("error looking up new member", e);
      }
    }

    GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, group);

    Set<UUID> membersToApprove = members.stream().map(JsonAddress::getUUID).collect(Collectors.toSet());
    GroupChange.Actions.Builder change = groupOperations.createApproveGroupJoinRequest(membersToApprove);
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
