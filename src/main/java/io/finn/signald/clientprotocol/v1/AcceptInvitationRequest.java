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
  public JsonGroupV2Info run(Request request)
      throws NoSuchAccountError, OwnProfileKeyDoesNotExistError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, InternalError, InvalidRequestError {
    Manager m = Common.getManager(account);
    Account a = Common.getAccount(account);

    ProfileKeyCredential ownProfileKeyCredential;
    try {
      ownProfileKeyCredential = m.getRecipientProfileKeyCredential(m.getOwnRecipient()).getProfileKeyCredential();
    } catch (InterruptedException | ExecutionException | TimeoutException | IOException | SQLException e) {
      throw new InternalError("error getting own profile key credential", e);
    }

    if (ownProfileKeyCredential == null) {
      throw new OwnProfileKeyDoesNotExistError();
    }

    GroupsTable.Group group = Common.getGroup(a, groupID);

    GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, group);
    GroupChange.Actions.Builder change = groupOperations.createAcceptInviteChange(ownProfileKeyCredential);
    change.setSourceUuid(UuidUtil.toByteString(m.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
