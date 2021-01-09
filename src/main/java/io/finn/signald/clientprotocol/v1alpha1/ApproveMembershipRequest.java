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

package io.finn.signald.clientprotocol.v1alpha1;

import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SignaldClientRequest(type = "approve_membership", ResponseClass = JsonGroupV2Info.class)
@Doc("approve a request to join a group")
public class ApproveMembershipRequest implements RequestType {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Required @Doc("list of requesting members to approve") public List<JsonAddress> members;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException, VerificationFailedException {
    Manager m = Manager.get(account);
    AccountData accountData = m.getAccountData();
    Group group = accountData.groupsV2.get(groupID);
    if (group == null) {
      request.error("group not found");
      return;
    }

    List<SignalServiceAddress> recipients = group.getMembers();
    recipients.addAll(members.stream().map(JsonAddress::getSignalServiceAddress).collect(Collectors.toSet()));

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(Manager.serviceConfiguration).forGroup(groupSecretParams);

    Set<UUID> membersToApprove = members.stream().map(JsonAddress::getUUID).collect(Collectors.toSet());
    GroupChange.Actions.Builder change = groupOperations.createApproveGroupJoinRequest(membersToApprove);

    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    Pair<DecryptedGroup, GroupChange> groupChangePair = m.getGroupsV2Manager().commitChange(group, change);
    group.group = groupChangePair.first();
    group.revision += 1;

    GroupMasterKey masterKey = group.getMasterKey();
    byte[] signedChange = groupChangePair.second().toByteArray();

    SignalServiceGroupV2.Builder groupBuilder = SignalServiceGroupV2.newBuilder(masterKey).withRevision(group.revision).withSignedGroupChange(signedChange);
    SignalServiceDataMessage.Builder updateMessage = SignalServiceDataMessage.newBuilder().asGroupMessage(groupBuilder.build());

    m.sendGroupV2Message(updateMessage, group.getSignalServiceGroupV2(), recipients);

    accountData.groupsV2.update(group);
    accountData.save();
    request.reply(group.getJsonGroupV2Info());
  }
}
