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
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SignaldClientRequest(type = "update_group", ResponseClass = JsonGroupV2Info.class)
@Doc("modify a group. only v2 groups for now")
@Deprecated
public class UpdateGroupRequest implements RequestType {
  @Doc("The account to interact with") @Required @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @ExampleValue(ExampleValue.GROUP_TITLE) @OneOfRequired({"addMembers", "removeMembers"}) public String title;

  @OneOfRequired({"title", "removeMembers"}) public List<JsonAddress> addMembers;

  @OneOfRequired({"title", "addMembers"}) public List<JsonAddress> removeMembers;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException, VerificationFailedException {
    Manager m = Manager.get(account);
    AccountData accountData = m.getAccountData();
    Group group = accountData.groupsV2.get(groupID);
    if (group == null) {
      request.error("group not found");
      return;
    }

    List<SignalServiceAddress> recipients = group.group.getMembersList().stream().map(this ::getMemberAddress).collect(Collectors.toList());

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(Manager.serviceConfiguration).forGroup(groupSecretParams);

    GroupChange.Actions.Builder change = null;

    if (title != null) {
      change = groupOperations.createModifyGroupTitle(title);
    }

    if (addMembers != null && addMembers.size() > 0) {
      Set<GroupCandidate> candidates = new HashSet<>();
      for (JsonAddress partial : addMembers) {
        SignalServiceAddress member = accountData.recipientStore.resolve(partial.getSignalServiceAddress());
        ProfileAndCredentialEntry profileAndCredentialEntry = accountData.profileCredentialStore.get(member);
        Optional<ProfileKeyCredential> profileKeyCredential = Optional.absent();
        if (profileAndCredentialEntry != null) {
          profileKeyCredential = Optional.fromNullable(profileAndCredentialEntry.getProfileKeyCredential());
        }
        UUID uuid = member.getUuid().get();
        candidates.add(new GroupCandidate(uuid, profileKeyCredential));
        recipients.add(member);
      }
      change = groupOperations.createModifyGroupMembershipChange(candidates, m.getUUID());
    }

    if (removeMembers != null && removeMembers.size() > 0) {
      Set<UUID> candidates = new HashSet<>();
      for (JsonAddress partial : removeMembers) {
        SignalServiceAddress member = accountData.recipientStore.resolve(partial.getSignalServiceAddress());
        if (member.getUuid().isPresent()) {
          candidates.add(member.getUuid().get());
        }
      }
      change = groupOperations.createRemoveMembersChange(candidates);
    }

    if (change == null) {
      request.error("no change requested");
      return;
    }

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

  private SignalServiceAddress getMemberAddress(DecryptedMember member) { return new SignalServiceAddress(UuidUtil.fromByteString(member.getUuid()), null); }
}
