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

import io.finn.signald.Empty;
import io.finn.signald.GroupsV2Manager;
import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.storage.AccountData;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64UrlSafe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SignaldClientRequest(type = "join_group_link", ResponseClass = Empty.class)
@Doc("Join a group using the a signal.group URL. DOES NOT WORK YET")
public class JoinGroupLinkRequest implements RequestType {
  @Doc("The account to interact with") @Required public String account;

  @Doc("The signal.group URL") @Required public String url;

  @Override
  public void run(Request request) throws URISyntaxException, IOException, InvalidInputException, NoSuchAccountException, InterruptedException, ExecutionException,
                                          TimeoutException, GroupLinkNotActiveException, VerificationFailedException, InvalidGroupStateException {
    URI parsedURL = new URI(url);
    assert parsedURL.getHost().equals("signal.group"); // TODO: real input validation. If you see this in the merge request please tell me
    String encoding = parsedURL.getFragment();

    assert encoding != null;
    assert encoding.length() == 0;

    byte[] bytes = Base64UrlSafe.decodePaddingAgnostic(encoding);
    GroupInviteLink groupInviteLink = GroupInviteLink.parseFrom(bytes);

    assert groupInviteLink.getContentsCase() == GroupInviteLink.ContentsCase.V1CONTENTS : "Unsupported group link";
    GroupInviteLink.GroupInviteLinkContentsV1 groupInviteLinkContentsV1 = groupInviteLink.getV1Contents();
    GroupMasterKey groupMasterKey = new GroupMasterKey(groupInviteLinkContentsV1.getGroupMasterKey().toByteArray());
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    byte[] password = groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray();

    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(Manager.serviceConfiguration).forGroup(groupSecretParams);
    Manager m = Manager.get(account);
    AccountData accountData = m.getAccountData();
    ProfileKeyCredential profileKeyCredential = accountData.profileCredentialStore.getCredential(m.getUUID(), m.getAccountData().getProfileKey(), m.getMessageReceiver());

    if (profileKeyCredential == null) {
      request.error("cannot get own profileKeyCredential");
      return;
    }
    GroupChange.Actions.Builder change = groupOperations.createGroupJoinRequest(profileKeyCredential);
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();

    DecryptedGroupJoinInfo groupJoinInfo = groupsV2Manager.getGroupJoinInfo(groupSecretParams, password);
    GroupChange groupChange = groupsV2Manager.commitJoinChangeWithConflictResolution(groupJoinInfo.getRevision(), change, groupSecretParams, password);
    DecryptedGroupChange decryptedChange = groupOperations.decryptChange(groupChange, false).get();

    request.reply(groupsV2Manager.getGroup(groupSecretParams, decryptedChange.getRevision()));
  }
}
