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

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonGroupJoinInfo;
import io.finn.signald.storage.Group;
import io.finn.signald.storage.GroupsV2Storage;
import io.finn.signald.storage.ProfileCredentialStore;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.*;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GroupsV2Manager {
  private final GroupsV2Api groupsV2Api;
  private final GroupsV2Storage storage;
  private final ProfileCredentialStore profileCredentialStore;
  private final UUID self;
  private final static GroupsV2Operations groupsV2Operations = GroupsUtil.GetGroupsV2Operations(Manager.serviceConfiguration);

  public GroupsV2Manager(GroupsV2Api groupsV2Api, GroupsV2Storage storage, ProfileCredentialStore profileCredentialStore, UUID self) {
    this.groupsV2Api = groupsV2Api;
    this.storage = storage;
    this.profileCredentialStore = profileCredentialStore;
    this.self = self;
  }

  public boolean handleIncomingDataMessage(SignalServiceDataMessage message) throws IOException, VerificationFailedException {
    assert message.getGroupContext().isPresent();
    assert message.getGroupContext().get().getGroupV2().isPresent();
    SignalServiceGroupV2 group = message.getGroupContext().get().getGroupV2().get();
    Group localState = storage.get(group);

    if (localState != null && localState.revision == group.getRevision()) {
      return false;
    }

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    try {
      getGroup(groupSecretParams, group.getRevision());
    } catch (InvalidGroupStateException e) {
      // do nothing for now
      e.printStackTrace();
    }
    return true;
  }

  public JsonGroupJoinInfo getGroupJoinInfo(String urlString) throws IOException, InvalidInputException, VerificationFailedException, GroupLinkNotActiveException {
    URI uri;
    try {
      uri = new URI(urlString);
    } catch (URISyntaxException e) {
      return null;
    }
    String encoding = uri.getFragment();
    if (encoding == null || encoding.length() == 0) {
      return null;
    }
    byte[] bytes = Base64UrlSafe.decodePaddingAgnostic(encoding);
    GroupInviteLink groupInviteLink = GroupInviteLink.parseFrom(bytes);
    GroupInviteLink.GroupInviteLinkContentsV1 groupInviteLinkContentsV1 = groupInviteLink.getV1Contents();
    GroupMasterKey groupMasterKey = new GroupMasterKey(groupInviteLinkContentsV1.getGroupMasterKey().toByteArray());
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    DecryptedGroupJoinInfo decryptedGroupJoinInfo = getGroupJoinInfo(groupSecretParams, groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray());
    return new JsonGroupJoinInfo(decryptedGroupJoinInfo, groupMasterKey);
  }

  public DecryptedGroupJoinInfo getGroupJoinInfo(GroupSecretParams groupSecretParams, byte[] password)
      throws IOException, VerificationFailedException, GroupLinkNotActiveException {
    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(self, today, groupSecretParams, authCredential);

    return groupsV2Api.getGroupJoinInfo(groupSecretParams, Optional.of(password), authorization);
  }

  public GroupChange commitJoinChangeWithConflictResolution(int currentRevision, GroupChange.Actions.Builder change, GroupSecretParams groupSecretParams, byte[] password)
      throws IOException, GroupLinkNotActiveException, VerificationFailedException {
    for (int attempt = 0; attempt < 5; attempt++) {
      try {
        GroupChange.Actions changeActions = change.setRevision(currentRevision + 1).build();
        GroupChange signedGroupChange = commitJoinToServer(changeActions, groupSecretParams, password);

        return signedGroupChange;
      } catch (ConflictException e) {
        currentRevision = getGroupJoinInfo(groupSecretParams, password).getRevision();
      }
    }
    return null;
  }

  public GroupChange commitJoinToServer(GroupChange.Actions change, GroupSecretParams groupSecretParams, byte[] password) throws IOException, VerificationFailedException {
    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredentialResponse = storage.getAuthCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authorizationString = groupsV2Api.getGroupsV2AuthorizationString(self, today, groupSecretParams, authCredentialResponse);
    return groupsV2Api.patchGroup(change, authorizationString, Optional.fromNullable(password));
  }

  public Group getGroup(String groupID, int revision) throws VerificationFailedException, InvalidGroupStateException, IOException {
    Group g = storage.get(groupID);
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(g.masterKey);
    return getGroup(groupSecretParams, revision);
  }

  public Group getGroup(GroupSecretParams groupSecretParams, int revision) throws InvalidGroupStateException, VerificationFailedException, IOException {
    String groupID = Base64.encodeBytes(groupSecretParams.getPublicParams().getGroupIdentifier().serialize());
    Group group = storage.get(groupID);
    if (group == null || group.revision < revision || revision < 0) {
      int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
      AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
      GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(self, today, groupSecretParams, authCredential);
      try {
        DecryptedGroup decryptedGroup = groupsV2Api.getGroup(groupSecretParams, authorization);
        group = new Group(groupSecretParams.getMasterKey(), decryptedGroup.getRevision(), decryptedGroup);
        storage.update(group);
      } catch (NotInGroupException e) {
        if (group != null) {
          storage.remove(group);
        }
      }
    }
    return group;
  }

  public Pair<DecryptedGroup, GroupChange> commitChange(Group group, GroupChange.Actions.Builder change) throws IOException, VerificationFailedException {
    final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);
    final DecryptedGroup previousGroupState = group.getGroup();
    final int nextRevision = previousGroupState.getRevision() + 1;
    final GroupChange.Actions changeActions = change.setRevision(nextRevision).build();
    final DecryptedGroupChange decryptedChange;
    final DecryptedGroup decryptedGroupState;

    try {
      decryptedChange = groupOperations.decryptChange(changeActions, self);
      decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
    } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
      throw new IOException(e);
    }

    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authString = groupsV2Api.getGroupsV2AuthorizationString(self, today, groupSecretParams, authCredential);
    GroupChange signedGroupChange = groupsV2Api.patchGroup(changeActions, authString, Optional.absent());

    return new Pair<>(decryptedGroupState, signedGroupChange);
  }
}
