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

import io.finn.signald.GroupsV2Manager;
import io.finn.signald.Manager;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.UnknownGroupException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@SignaldClientRequest(type = "leave_group")
public class LeaveGroupRequest implements RequestType<GroupInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Doc("The group to leave") @Required public String groupID;

  @Override
  public GroupInfo run(Request request)
      throws IOException, NoSuchAccountException, NotAGroupMemberException, GroupNotFoundException, UnknownGroupException, VerificationFailedException, SQLException {
    Manager m = Manager.get(account);

    if (groupID.length() == 24) { // legacy (v1) group
      m.sendQuitGroupMessage(Base64.decode(groupID));
      io.finn.signald.storage.GroupInfo g = m.getAccountData().groupStore.getGroup(groupID);
      return new GroupInfo(g);
    }

    AccountData accountData = m.getAccountData();
    Group group = accountData.groupsV2.get(groupID);
    if (group == null) {
      throw new UnknownGroupException();
    }

    List<SignalServiceAddress> recipients = group.group.getMembersList().stream().map(UpdateGroupRequest::getMemberAddress).collect(Collectors.toList());

    GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();
    Pair<SignalServiceDataMessage.Builder, Group> output = groupsV2Manager.leaveGroup(groupID);

    m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2(), recipients);
    accountData.groupsV2.update(output.second());
    accountData.save();
    return new GroupInfo(output.second().getJsonGroupV2Info(m));
  }
}
