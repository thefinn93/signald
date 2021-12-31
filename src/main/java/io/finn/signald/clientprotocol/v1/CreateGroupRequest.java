/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.signal.storageservice.protos.groups.Member;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.InvalidKeyException;
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
    if (m.getAccountData().profileCredentialStore.getProfileKeyCredential(m.getACI()) == null) {
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

    File avatarFile = avatar == null ? null : new File(avatar);

    GroupsTable.Group group;
    try {
      group = Common.getGroups(Common.getAccount(account)).createGroup(title, avatarFile, recipients, role, timer);
    } catch (IOException | SQLException | InvalidInputException | ServerNotFoundException | NoSuchAccountException | InvalidKeyException | InvalidProxyException e) {
      throw new InternalError("error creating group", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    }

    Common.saveAccount(m.getAccountData());
    SignalServiceGroupV2 signalServiceGroupV2 = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(group.getRevision()).build();
    SignalServiceDataMessage.Builder message = SignalServiceDataMessage.newBuilder().asGroupMessage(signalServiceGroupV2);
    try {
      m.sendGroupV2Message(message, group);
    } catch (SQLException | IOException e) {
      throw new InternalError("error notifying new members of group", e);
    }
    return new JsonGroupV2Info(group);
  }
}
