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
import io.finn.signald.GroupInviteLinkUrl;
import io.finn.signald.Groups;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("join_group")
@Doc("Join a group using the a signal.group URL. Note that you must have a profile name set to join groups.")
public class JoinGroupRequest implements RequestType<JsonGroupJoinInfo> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_JOIN_URI) @Doc("The signal.group URL") @Required public String uri;

  @Override
  public JsonGroupJoinInfo run(Request request) throws InvalidRequestError, InvalidInviteURIError, InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError,
                                                       OwnProfileKeyDoesNotExistError, GroupVerificationError, GroupNotActiveError, UnknownGroupError, InvalidGroupStateError {
    Account a = Common.getAccount(account);
    Groups groups = Common.getGroups(a);

    GroupInviteLinkUrl groupInviteLinkUrl;
    try {
      groupInviteLinkUrl = GroupInviteLinkUrl.fromUri(uri);
    } catch (GroupInviteLinkUrl.InvalidGroupLinkException | GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
      throw new InvalidRequestError(e.getMessage());
    }
    if (groupInviteLinkUrl == null) {
      throw new InvalidInviteURIError();
    }

    Manager m = Common.getManager(account);
    ProfileKeyCredential profileKeyCredential;
    try {
      profileKeyCredential = m.getRecipientProfileKeyCredential(m.getOwnRecipient()).getProfileKeyCredential();
    } catch (InterruptedException | ExecutionException | TimeoutException | IOException | SQLException e) {
      throw new InternalError("error getting own profile key credential", e);
    }

    if (profileKeyCredential == null) {
      throw new OwnProfileKeyDoesNotExistError();
    }

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupInviteLinkUrl.getGroupMasterKey());

    GroupsV2Operations.GroupOperations groupOperations;
    try {
      GroupsV2Operations operations = GroupsUtil.GetGroupsV2Operations(a.getServiceConfiguration());
      groupOperations = operations.forGroup(groupSecretParams);
    } catch (SQLException | IOException e) {
      throw new InternalError("error getting service configuration", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }

    DecryptedGroupJoinInfo groupJoinInfo;
    try {
      groupJoinInfo = groups.getGroupJoinInfo(groupSecretParams, groupInviteLinkUrl.getPassword().serialize());
    } catch (SQLException | IOException | InvalidInputException e) {
      throw new InternalError("error getting service configuration", e);
    } catch (GroupLinkNotActiveException e) {
      throw new GroupNotActiveError(e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    }

    boolean requestToJoin = groupJoinInfo.getAddFromInviteLink() == AccessControl.AccessRequired.ADMINISTRATOR;
    GroupChange.Actions.Builder change;
    if (requestToJoin) {
      change = groupOperations.createGroupJoinRequest(profileKeyCredential);
    } else {
      change = groupOperations.createGroupJoinDirect(profileKeyCredential);
    }
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    int revision = groupJoinInfo.getRevision() + 1;

    DecryptedGroupChange decryptedChange;
    GroupChange groupChange;
    try {
      groupChange = groups.commitJoinToServer(change.build(), groupInviteLinkUrl);
      decryptedChange = groupOperations.decryptChange(groupChange, false).get();
    } catch (IOException | SQLException | InvalidInputException e) {
      throw new InternalError("error committing group join change", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    }

    Optional<GroupsTable.Group> groupOptional;
    try {
      groupOptional = groups.getGroup(groupSecretParams, decryptedChange.getRevision());
    } catch (IOException | SQLException | InvalidInputException e) {
      throw new InternalError("error getting new group information after join", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    }

    if (groupOptional.isPresent()) {
      throw new UnknownGroupError();
    }

    GroupsTable.Group group = groupOptional.get();

    SignalServiceGroupV2.Builder groupBuilder = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(revision).withSignedGroupChange(groupChange.toByteArray());
    SignalServiceDataMessage.Builder updateMessage =
        SignalServiceDataMessage.newBuilder().asGroupMessage(groupBuilder.build()).withExpiration(group.getDecryptedGroup().getDisappearingMessagesTimer().getDuration());
    try {
      m.sendGroupV2Message(updateMessage, group);
    } catch (SQLException | IOException e) {
      throw new InternalError("error sending group update message", e);
    }

    return new JsonGroupJoinInfo(groupJoinInfo, groupInviteLinkUrl.getGroupMasterKey());
  }
}
