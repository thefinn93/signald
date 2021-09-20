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
import io.finn.signald.storage.Group;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.signal.storageservice.protos.groups.Member;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

@ProtocolType("create_group")
public class CreateGroupRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Required @ExampleValue(ExampleValue.GROUP_TITLE) public String title;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) public String avatar;
  @Required public List<JsonAddress> members;

  @JsonProperty("member_role")
  @Doc("The role of all members other than the group creator. Options are ADMINISTRATOR or DEFAULT (case insensitive)")
  @ExampleValue("\"ADMINISTRATOR\"")
  public String memberRole;

  @Doc("the message expiration timer") public int timer;

  @Override
  public JsonGroupV2Info run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, OwnProfileKeyDoesNotExistError, NoKnownUUIDError,
                                                     InvalidRequestError, GroupVerificationError, InvalidGroupStateError, UnknownGroupError {
    Manager m = Common.getManager(account);
    RecipientsTable recipientsTable = m.getRecipientsTable();
    List<Recipient> recipients = new ArrayList<>();
    if (m.getAccountData().profileCredentialStore.getProfileKeyCredential(m.getUUID()) == null) {
      throw new OwnProfileKeyDoesNotExistError();
    }
    for (JsonAddress member : members) {
      Recipient recipient = Common.getRecipient(recipientsTable, member);
      try {
        m.getRecipientProfileKeyCredential(recipient);
      } catch (InterruptedException | ExecutionException | TimeoutException | IOException | SQLException e) {
        throw new InternalError("error getting recipient profile key", e);
      }
      recipients.add(recipient);
      if (recipient.getUUID() == null) {
        throw new NoKnownUUIDError(recipient.getAddress().getNumber().orNull());
      }
    }

    Member.Role role = Member.Role.DEFAULT;
    if (memberRole != null) {
      switch (memberRole.toUpperCase()) {
      case "ADMINISTRATOR":
        role = Member.Role.ADMINISTRATOR;
        break;
      case "DEFAULT":
        role = Member.Role.DEFAULT;
        break;
      default:
        throw new InvalidRequestError("member_role must be ADMINISTRATOR or DEFAULT");
      }
    }

    Group group = null;
    try {
      group = m.getGroupsV2Manager().createGroup(title, avatar, recipients, role, timer);
    } catch (IOException e) {
      throw new InternalError("error creating group", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    }
    Common.saveAccount(m.getAccountData());
    SignalServiceGroupV2 signalServiceGroupV2 = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(group.revision).build();
    SignalServiceDataMessage.Builder message = SignalServiceDataMessage.newBuilder().asGroupMessage(signalServiceGroupV2);
    try {
      m.sendGroupV2Message(message, signalServiceGroupV2);
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (SQLException | IOException e) {
      throw new InternalError("error notifying new members of group", e);
    }
    return group.getJsonGroupV2Info(m);
  }
}
