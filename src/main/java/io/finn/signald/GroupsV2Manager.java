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
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.GroupsV2Storage;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GroupsV2Manager {
  private final GroupsV2Api groupsV2Api;
  private final GroupsV2Storage storage;
  private final UUID uuid;

  public GroupsV2Manager(GroupsV2Api groupsV2Api, GroupsV2Storage storage, UUID uuid) {
    this.groupsV2Api = groupsV2Api;
    this.storage = storage;
    this.uuid = uuid;
  }

  public boolean handleIncomingDataMessage(SignalServiceDataMessage message) throws IOException, VerificationFailedException, InvalidGroupStateException {
    assert message.getGroupContext().isPresent();
    assert message.getGroupContext().get().getGroupV2().isPresent();
    SignalServiceGroupV2 group = message.getGroupContext().get().getGroupV2().get();
    JsonGroupV2Info localState = storage.get(group);

    if (localState != null && localState.revision == group.getRevision()) {
      return false;
    }

    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(uuid, today, groupSecretParams, authCredential);
    try {
      JsonGroupV2Info newState = new JsonGroupV2Info(group, groupsV2Api.getGroup(groupSecretParams, authorization));
      if (localState == null) {
        storage.groups.add(newState);
      } else {
        localState.update(newState);
      }
    } catch (NotInGroupException e) {
      storage.remove(group);
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

    byte[] password = groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray();

    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(uuid, today, groupSecretParams, authCredential);

    DecryptedGroupJoinInfo joinInfo = groupsV2Api.getGroupJoinInfo(groupSecretParams, Optional.of(password), authorization);
    return new JsonGroupJoinInfo(joinInfo);
  }
}
