/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.storageservice.protos.groups.GroupChange;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("accept_invitation")
@Doc("Accept a v2 group invitation. Note that you must have a profile name set to join groups.")
@ErrorDoc(error = AuthorizationFailedError.class, doc = AuthorizationFailedError.DEFAULT_ERROR_DOC)
@ErrorDoc(error = GroupPatchNotAcceptedError.class, doc = GroupPatchNotAcceptedError.DEFAULT_ERROR_DOC)
public class AcceptInvitationRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, OwnProfileKeyDoesNotExistError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError, SQLError, GroupPatchNotAcceptedError {
    Manager m = Common.getManager(account);
    Account a = Common.getAccount(account);

    ProfileKeyCredential ownProfileKeyCredential;
    try {
      ownProfileKeyCredential = a.getDB().ProfileKeysTable.getProfileKeyCredential(a.getSelf());
    } catch (IOException | SQLException | InvalidInputException e) {
      throw new InternalError("error getting own profile key credential", e);
    }

    if (ownProfileKeyCredential == null) {
      throw new OwnProfileKeyDoesNotExistError();
    }

    var group = Common.getGroup(a, groupID);

    GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, group);
    GroupChange.Actions.Builder change = groupOperations.createAcceptInviteChange(ownProfileKeyCredential);
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
