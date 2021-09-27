/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("refuse_membership")
@Doc("deny a request to join a group")
public class RefuseMembershipRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @JsonProperty("group_id") @Required public String groupID;

  @Required @Doc("list of requesting members to refuse") public List<JsonAddress> members;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, GroupVerificationError, InternalError {
    Manager m = Common.getManager(account);
    AccountData accountData = m.getAccountData();
    Group group;
    try {
      group = accountData.groupsV2.get(groupID);
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    }

    RecipientsTable recipientsTable = m.getRecipientsTable();
    List<Recipient> recipients;
    try {
      recipients = recipientsTable.get(group.getMembers());
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipients", e);
    }
    for (JsonAddress member : members) {
      try {
        recipients.add(recipientsTable.get(member));
      } catch (SQLException | IOException e) {
        throw new InternalError("error looking up new member", e);
      }
    }

    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(m.getServiceConfiguration()).forGroup(groupSecretParams);

    Set<UUID> membersToRefuse = new HashSet<>();
    for (JsonAddress member : members) {
      membersToRefuse.add(Common.getRecipient(recipientsTable, member).getUUID());
    }
    GroupChange.Actions.Builder change = groupOperations.createRefuseGroupJoinRequest(membersToRefuse);

    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    Pair<DecryptedGroup, GroupChange> groupChangePair = null;
    try {
      groupChangePair = m.getGroupsV2Manager().commitChange(group, change);
    } catch (IOException e) {
      throw new InternalError("error committing group change", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    }
    group.group = groupChangePair.first();
    group.revision += 1;

    GroupMasterKey masterKey = group.getMasterKey();
    byte[] signedChange = groupChangePair.second().toByteArray();

    SignalServiceGroupV2.Builder groupBuilder = SignalServiceGroupV2.newBuilder(masterKey).withRevision(group.revision).withSignedGroupChange(signedChange);
    SignalServiceDataMessage.Builder updateMessage = SignalServiceDataMessage.newBuilder().asGroupMessage(groupBuilder.build());

    try {
      m.sendGroupV2Message(updateMessage, group.getSignalServiceGroupV2(), recipients);
    } catch (SQLException | IOException e) {
      throw new InternalError("error sending update message", e);
    }

    accountData.groupsV2.update(group);
    Common.saveAccount(accountData);

    return group.getJsonGroupV2Info(m);
  }
}