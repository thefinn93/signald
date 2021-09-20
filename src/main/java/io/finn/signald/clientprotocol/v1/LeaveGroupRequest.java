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
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.util.Base64;

@ProtocolType("leave_group")
public class LeaveGroupRequest implements RequestType<GroupInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Doc("The group to leave") @Required public String groupID;

  @Override
  public GroupInfo run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, UnknownGroupError, GroupVerificationError {
    Manager m = Common.getManager(account);

    if (groupID.length() == 24) { // legacy (v1) group
      try {
        m.sendQuitGroupMessage(Base64.decode(groupID));
      } catch (GroupNotFoundException | NotAGroupMemberException e) {
        throw new UnknownGroupError();
      } catch (IOException | SQLException e) {
        throw new InternalError("error sending group quit message", e);
      }
      io.finn.signald.storage.GroupInfo g = m.getAccountData().groupStore.getGroup(groupID);
      return new GroupInfo(g);
    }

    AccountData accountData = m.getAccountData();
    Group group;
    try {
      group = accountData.groupsV2.get(groupID);
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    }
    if (group == null) {
      throw new UnknownGroupError();
    }

    List<Recipient> recipients = Common.getRecipient(m.getRecipientsTable(), group.group.getMembersList().stream().map(GroupsUtil::getMemberAddress).collect(Collectors.toList()));

    GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();
    Pair<SignalServiceDataMessage.Builder, Group> output;
    try {
      output = groupsV2Manager.leaveGroup(groupID);
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (IOException e) {
      throw new InternalError("error leabing group", e);
    }

    try {
      m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2(), recipients);
    } catch (IOException | SQLException e) {
      throw new InternalError("error sending group v2 message", e);
    }
    accountData.groupsV2.remove(output.second());
    Common.saveAccount(accountData);
    return new GroupInfo(output.second().getJsonGroupV2Info(m));
  }
}
