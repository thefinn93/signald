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

package io.finn.signald.clientprotocol.v1alpha2;

import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.exceptions.UnknownGroupException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static io.finn.signald.annotations.ExactlyOneOfRequired.GROUP_MODIFICATION;

@SignaldClientRequest(type = "update_group", ResponseClass = GroupInfo.class)
@Doc("modify a group")
@Deprecated
public class UpdateGroupRequest implements RequestType {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The identifier of the account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupID;

  @ExampleValue(ExampleValue.GROUP_TITLE) @ExactlyOneOfRequired(GROUP_MODIFICATION) public String title;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) @ExactlyOneOfRequired(GROUP_MODIFICATION) public String avatar;

  @ExactlyOneOfRequired(GROUP_MODIFICATION) public List<JsonAddress> addMembers;

  @ExactlyOneOfRequired(GROUP_MODIFICATION) public List<JsonAddress> removeMembers;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException, VerificationFailedException, GroupNotFoundException, NotAGroupMemberException,
                                          AttachmentInvalidException, InterruptedException, ExecutionException, TimeoutException, UnknownGroupException, SQLException {
    Manager m = Manager.get(account);
    AccountData accountData = m.getAccountData();

    if (groupID.length() == 24) { // v1 group
      List<SignalServiceAddress> addMembersSignalServiceAddress = null;
      if (addMembers != null) {
        addMembersSignalServiceAddress = addMembers.stream().map(JsonAddress::getSignalServiceAddress).collect(Collectors.toList());
      }
      m.sendUpdateGroupMessage(Base64.decode(groupID), title, addMembersSignalServiceAddress, avatar);
    } else {
      Group group = accountData.groupsV2.get(groupID);

      List<SignalServiceAddress> recipients = group.group.getMembersList().stream().map(this ::getMemberAddress).collect(Collectors.toList());
      Pair<SignalServiceDataMessage.Builder, Group> output;

      if (title != null) {
        output = m.getGroupsV2Manager().updateTitle(groupID, title);
      } else if (avatar != null) {
        output = m.getGroupsV2Manager().updateAvatar(groupID, avatar);
      } else if (addMembers != null && addMembers.size() > 0) {
        List<ProfileAndCredentialEntry> members = new ArrayList<>();
        for (JsonAddress member : addMembers) {
          SignalServiceAddress signalServiceAddress = m.getAccountData().recipientStore.resolve(member.getSignalServiceAddress());
          ProfileAndCredentialEntry profileAndCredentialEntry = m.getRecipientProfileKeyCredential(signalServiceAddress);
          if (profileAndCredentialEntry == null) {
            logger.warn("Unable to add group member with no profile");
            continue;
          }
          members.add(profileAndCredentialEntry);
          recipients.add(profileAndCredentialEntry.getServiceAddress());
        }
        output = m.getGroupsV2Manager().addMembers(groupID, members);
      } else if (removeMembers != null && removeMembers.size() > 0) {
        Set<UUID> members = new HashSet<>();
        for (JsonAddress member : removeMembers) {
          SignalServiceAddress signalServiceAddress = member.getSignalServiceAddress();
          if (!signalServiceAddress.getUuid().isPresent()) {
            signalServiceAddress = m.getAccountData().recipientStore.resolve(member.getSignalServiceAddress());
          }
          if (!signalServiceAddress.getUuid().isPresent()) {
            logger.warn("cannot remove member from group if we do not have their UUID! How did they get into the group if we don't know their UUID?",
                        new JsonAddress(signalServiceAddress).toRedactedString());
          }
          members.add(signalServiceAddress.getUuid().get());
        }
        output = m.getGroupsV2Manager().removeMembers(groupID, members);
      } else {
        request.error("no change requested");
        return;
      }

      m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2(), recipients);

      accountData.groupsV2.update(output.second());
      accountData.save();
      request.reply(new GroupInfo(group.getJsonGroupV2Info(m)));
    }
  }

  private SignalServiceAddress getMemberAddress(DecryptedMember member) { return new SignalServiceAddress(UuidUtil.fromByteString(member.getUuid()), null); }
}
