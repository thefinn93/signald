/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.signal.storageservice.protos.groups.GroupChange;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("refuse_membership")
@Doc("deny a request to join a group")
public class RefuseMembershipRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @JsonProperty("group_id") @Required public String groupID;

  @Required @Doc("list of requesting members to refuse") public List<JsonAddress> members;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, GroupVerificationError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError, UnregisteredUserError, SQLError {
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
    for (JsonAddress member : members) {
      membersToRefuse.add(Common.getRecipient(a.getACI(), member).getUUID());
    }

    GroupChange.Actions.Builder change = Common.getGroupOperations(a, group).createRefuseGroupJoinRequest(membersToRefuse);
    change.setSourceUuid(UuidUtil.toByteString(a.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
