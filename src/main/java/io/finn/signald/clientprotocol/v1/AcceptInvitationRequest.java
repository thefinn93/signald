/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("accept_invitation")
@Doc("Accept a v2 group invitation. Note that you must have a profile name set to join groups.")
public class AcceptInvitationRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, OwnProfileKeyDoesNotExistError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError {
    Manager m = Common.getManager(account);
    Account a = Common.getAccount(account);

    ProfileKeyCredential ownProfileKeyCredential;
    try {
      ownProfileKeyCredential = m.getRecipientProfileKeyCredential(m.getOwnRecipient()).getProfileKeyCredential();
    } catch (InterruptedException | ExecutionException | TimeoutException | IOException | SQLException e) {
      throw new InternalError("error getting own profile key credential", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
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
