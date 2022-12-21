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
import io.finn.signald.db.IGroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.storageservice.protos.groups.Member;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

@ProtocolType("create_group")
public class CreateGroupRequest implements RequestType<JsonGroupV2Info> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @Required @ExampleValue(ExampleValue.GROUP_TITLE) public String title;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) public String avatar;
  @Required public List<JsonAddress> members;

  @JsonProperty("member_role")
  @Doc("The role of all members other than the group creator. Options are ADMINISTRATOR or DEFAULT (case insensitive)")
  @ExampleValue("\"ADMINISTRATOR\"")
  public String memberRole;

  @Doc("the message expiration timer") public int timer;

  @Override
  public JsonGroupV2Info run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, OwnProfileKeyDoesNotExistError, NoKnownUUIDError, InvalidRequestError,
             GroupVerificationError, InvalidGroupStateError, UnknownGroupError, UnregisteredUserError, AuthorizationFailedError, SQLError {
    Account a = Common.getAccount(account);
    List<Recipient> recipients = new ArrayList<>();

    try {
      if (a.getDB().ProfileKeysTable.getProfileKey(a.getSelf()) == null) {
        throw new OwnProfileKeyDoesNotExistError();
      }
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (IOException e) {
      throw new InternalError("unexpected error verifying own profile key exists", e);
    }

    var recipientsTable = Database.Get(a.getACI()).RecipientsTable;
    Set<GroupCandidate> candidates = new HashSet<>();
    for (JsonAddress member : members) {
      try {
        Recipient recipient = recipientsTable.get(member);
        ExpiringProfileKeyCredential expiringProfileKeyCredential = a.getDB().ProfileKeysTable.getExpiringProfileKeyCredential(recipient);
        recipients.add(recipientsTable.get(recipient.getAddress()));
        UUID uuid = recipient.getUUID();
        candidates.add(new GroupCandidate(uuid, Optional.ofNullable(expiringProfileKeyCredential)));
      } catch (InvalidInputException | SQLException | IOException e) {
        throw new InternalError("error adding member to group", e);
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

    IGroupsTable.IGroup group;
    try {
      group = Common.getGroups(Common.getAccount(account)).createGroup(title, avatarFile, candidates, role, timer);
    } catch (IOException | SQLException | InvalidInputException | InvalidKeyException e) {
      throw new InternalError("error creating group", e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }

    SignalServiceGroupV2 signalServiceGroupV2 = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(group.getRevision()).build();
    SignalServiceDataMessage.Builder message = SignalServiceDataMessage.newBuilder().asGroupMessage(signalServiceGroupV2);

    Common.sendGroupUpdateMessage(a, message, group);

    return new JsonGroupV2Info(group);
  }
}
