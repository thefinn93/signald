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
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.GroupsTable;
import java.sql.SQLException;

@ProtocolType("list_groups")
public class ListGroupsRequest implements RequestType<GroupList> {

  @Required public String account;

  @Override
  public GroupList run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRequestError {
    GroupList groups = new GroupList();
    Account a = Common.getAccount(account);

    try {
      for (GroupsTable.Group g : a.getGroupsTable().getAll()) {
        groups.add(g.getJsonGroupV2Info());
      }
    } catch (SQLException e) {
      throw new InternalError("error listing groups", e);
    }

    Manager m = Common.getManager(account);
    for (io.finn.signald.storage.GroupInfo g : m.getAccountData().groupStore.getGroups()) {
      groups.add(new JsonGroupInfo(g));
    }

    return groups;
  }
}
