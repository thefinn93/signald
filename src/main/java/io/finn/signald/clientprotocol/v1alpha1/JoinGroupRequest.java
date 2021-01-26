/*
 * Copyright (C) 2020 Finn Herzfeld
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

import io.finn.signald.GroupInviteLinkUrl;
import io.finn.signald.GroupsV2Manager;
import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.UnknownGroupException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SignaldClientRequest(type = "join_group", ResponseClass = JsonGroupJoinInfo.class)
@Doc("Join a group using the a signal.group URL. Note that you must have a profile name set to join groups.")
@Deprecated
public class JoinGroupRequest implements RequestType {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_JOIN_URI) @Doc("The signal.group URL") @Required public String uri;

  @Override
  public void run(Request request)
      throws GroupInviteLinkUrl.InvalidGroupLinkException, GroupInviteLinkUrl.UnknownGroupLinkVersionException, IOException, NoSuchAccountException, InterruptedException,
             ExecutionException, TimeoutException, GroupLinkNotActiveException, VerificationFailedException, InvalidGroupStateException, UnknownGroupException {
    GroupInviteLinkUrl groupInviteLinkUrl = GroupInviteLinkUrl.fromUri(uri);
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInviteLinkUrl.getGroupMasterKey());

    Manager m = Manager.get(account);
    ProfileKeyCredential profileKeyCredential = m.getRecipientProfileKeyCredential(m.getOwnAddress()).getProfileKeyCredential();

    if (profileKeyCredential == null) {
      request.error("cannot get own profileKeyCredential");
      return;
    }

    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(Manager.serviceConfiguration).forGroup(groupSecretParams);
    GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();
    DecryptedGroupJoinInfo groupJoinInfo = groupsV2Manager.getGroupJoinInfo(groupSecretParams, groupInviteLinkUrl.getPassword().serialize());

    boolean requestToJoin = groupJoinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;
    GroupChange.Actions.Builder change = requestToJoin ? groupOperations.createGroupJoinRequest(profileKeyCredential) : groupOperations.createGroupJoinDirect(profileKeyCredential);
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    int revision = groupJoinInfo.getRevision() + 1;

    GroupChange groupChange = groupsV2Manager.commitJoinChangeWithConflictResolution(revision, change, groupSecretParams, groupInviteLinkUrl.getPassword().serialize());
    DecryptedGroupChange decryptedChange = groupOperations.decryptChange(groupChange, false).get();

    Group group = groupsV2Manager.getGroup(groupSecretParams, decryptedChange.getRevision());

    if (group != null) {
      SignalServiceGroupV2.Builder groupBuilder = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(revision).withSignedGroupChange(groupChange.toByteArray());
      SignalServiceDataMessage.Builder updateMessage = SignalServiceDataMessage.newBuilder().asGroupMessage(groupBuilder.build()).withExpiration(group.getTimer());
      m.sendGroupV2Message(updateMessage, group.getSignalServiceGroupV2());

      AccountData accountData = m.getAccountData();
      accountData.groupsV2.update(group);
      accountData.save();
    }
    request.reply(new JsonGroupJoinInfo(groupJoinInfo, groupInviteLinkUrl.getGroupMasterKey()));
  }
}
