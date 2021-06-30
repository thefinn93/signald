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
import io.finn.signald.clientprotocol.v1.exceptions.InvalidRequestException;
import io.finn.signald.clientprotocol.v1.exceptions.NoKnownUUID;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.OwnProfileKeyDoesNotExist;
import io.finn.signald.clientprotocol.v1.exceptions.UnknownGroupException;
import io.finn.signald.storage.AddressResolver;
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
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

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
  public JsonGroupV2Info run(Request request)
      throws IOException, NoSuchAccount, InvalidRequestException, InvalidGroupStateException, VerificationFailedException, UnknownGroupException, SQLException,
             InterruptedException, ExecutionException, TimeoutException, NoKnownUUID, OwnProfileKeyDoesNotExist {
    Manager m = Utils.getManager(account);
    AddressResolver resolver = m.getResolver();
    List<SignalServiceAddress> resolvedMembers = new ArrayList<>();
    if (m.getAccountData().profileCredentialStore.getProfileKeyCredential(m.getUUID()) == null) {
      throw new OwnProfileKeyDoesNotExist();
    }
    for (JsonAddress member : members) {
      SignalServiceAddress address = resolver.resolve(member.getSignalServiceAddress());
      m.getRecipientProfileKeyCredential(address);
      resolvedMembers.add(address);
      if (!address.getUuid().isPresent()) {
        throw new NoKnownUUID(address.getNumber().orNull());
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
        throw new InvalidRequestException("member_role must be ADMINISTRATOR or DEFAULT");
      }
    }

    Group group = m.getGroupsV2Manager().createGroup(title, avatar, resolvedMembers, role, timer);
    m.getAccountData().save();
    SignalServiceGroupV2 signalServiceGroupV2 = SignalServiceGroupV2.newBuilder(group.getMasterKey()).withRevision(group.revision).build();
    SignalServiceDataMessage.Builder message = SignalServiceDataMessage.newBuilder().asGroupMessage(signalServiceGroupV2);
    try {
      m.sendGroupV2Message(message, signalServiceGroupV2);
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupException();
    }
    return group.getJsonGroupV2Info(m);
  }
}
