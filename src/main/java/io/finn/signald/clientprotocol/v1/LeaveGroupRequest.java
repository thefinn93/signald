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
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;

@ProtocolType("leave_group")
public class LeaveGroupRequest implements RequestType<GroupInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Doc("The group to leave") @Required public String groupID;

  @Override
  public GroupInfo run(Request request)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, UnknownGroupError, GroupVerificationError, InvalidRequestError {
    Account a = Common.getAccount(account);

    GroupsTable.Group group = Common.getGroup(a, groupID);

    List<Recipient> recipients;
    try {
      recipients = group.getMembers();
    } catch (IOException | SQLException e) {
      throw new InternalError("error getting recipient list", e);
    }

    GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, group);

    List<DecryptedPendingMember> pendingMemberList = group.getDecryptedGroup().getPendingMembersList();
    Optional<DecryptedPendingMember> selfPendingMember = DecryptedGroupUtil.findPendingByUuid(pendingMemberList, a.getUUID());
    GroupChange.Actions.Builder change;
    if (selfPendingMember.isPresent()) {
      final Set<UuidCiphertext> uuidCipherTexts;
      try {
        uuidCipherTexts = group.getPendingMembers().stream().map(LeaveGroupRequest::recipientToUuidCipherText).collect(Collectors.toSet());
      } catch (IOException | SQLException e) {
        throw new InternalError("error getting pending member list", e);
      }
      change = groupOperations.createRemoveInvitationChange(uuidCipherTexts);
    } else {
      Set<UUID> uuidsToRemove = new HashSet<>();
      uuidsToRemove.add(a.getUUID());
      change = groupOperations.createRemoveMembersChange(uuidsToRemove);
    }

    Pair<SignalServiceDataMessage.Builder, GroupsTable.Group> updateOutput;
    try {
      updateOutput = Common.getGroups(a).updateGroup(group, change);
    } catch (IOException | VerificationFailedException | SQLException | InvalidInputException e) {
      throw new InternalError("error committing group change", e);
    }

    try {
      Common.getManager(account).sendGroupV2Message(updateOutput.first(), group.getSignalServiceGroupV2(), recipients);
    } catch (IOException | SQLException e) {
      throw new InternalError("error sending group update", e);
    }

    try {
      group.delete();
    } catch (SQLException e) {
      throw new InternalError("error removing group from local storage", e);
    }
    return new GroupInfo(new JsonGroupV2Info(updateOutput.second()));
  }

  private static UuidCiphertext recipientToUuidCipherText(Recipient recipient) {
    try {
      return new UuidCiphertext(recipient.getUUID().toString().getBytes());
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}
