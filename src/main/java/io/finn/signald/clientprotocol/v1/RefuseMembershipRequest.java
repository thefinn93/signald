/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;
import io.finn.signald.Account;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("refuse_membership")
@Doc("deny a request to join a group")
@ErrorDoc(error = AuthorizationFailedError.class, doc = AuthorizationFailedError.DEFAULT_ERROR_DOC)
@ErrorDoc(error = GroupPatchNotAcceptedError.class, doc = GroupPatchNotAcceptedError.DEFAULT_ERROR_DOC)
public class RefuseMembershipRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @JsonProperty("group_id") @Required public String groupID;

  @Required @Doc("list of requesting members to refuse") public List<JsonAddress> members;

  @JsonProperty("also_ban") public boolean alsoBan = false;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, GroupVerificationError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError, UnregisteredUserError, SQLError, GroupPatchNotAcceptedError {
    Account a = Common.getAccount(account);
    var group = Common.getGroup(a, groupID);

    List<Recipient> recipients;
    try {
      recipients = group.getMembers();
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipients", e);
    }

    var recipientsTable = Database.Get(a.getACI()).RecipientsTable;
    for (JsonAddress member : members) {
      try {
        recipients.add(recipientsTable.get(member));
      } catch (SQLException | IOException e) {
        throw new InternalError("error looking up new member", e);
      }
    }

    Set<UUID> membersToRefuse = new HashSet<>();
    List<DecryptedBannedMember> bannedMembers = new ArrayList<>();

    for (JsonAddress member : members) {
      UUID memberUUID = Common.getRecipient(a.getACI(), member).getUUID();
      membersToRefuse.add(memberUUID);
      if (alsoBan) {
        ByteString uuidByteString = UuidUtil.toByteString(memberUUID);
        DecryptedBannedMember bannedMember = DecryptedBannedMember.newBuilder().setUuid(uuidByteString).build();
        bannedMembers.add(bannedMember);
      }
    }

    GroupChange.Actions.Builder change = Common.getGroupOperations(a, group).createRefuseGroupJoinRequest(membersToRefuse, alsoBan, bannedMembers);
    change.setSourceUuid(UuidUtil.toByteString(a.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
