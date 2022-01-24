/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.GROUP_MODIFICATION;

import io.finn.signald.Account;
import io.finn.signald.GroupLinkPassword;
import io.finn.signald.Groups;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
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
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.util.Base64;

@ProtocolType("update_group")
@Doc("modify a group. Note that only one modification action may be performed at once")
public class UpdateGroupRequest implements RequestType<GroupInfo> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The identifier of the account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @Doc("the ID of the group to update") @Required public String groupID;

  @ExampleValue(ExampleValue.GROUP_TITLE) @ExactlyOneOfRequired(GROUP_MODIFICATION) public String title;

  @ExampleValue(ExampleValue.GROUP_DESCRIPTION)
  @ExactlyOneOfRequired(GROUP_MODIFICATION)
  @Doc("A new group description. Set to empty string to remove an existing description.")
  public String description;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) @ExactlyOneOfRequired(GROUP_MODIFICATION) public String avatar;

  @Doc("update the group timer.") @ExactlyOneOfRequired(GROUP_MODIFICATION) public int updateTimer = -1;

  @ExactlyOneOfRequired(GROUP_MODIFICATION) public List<JsonAddress> addMembers;

  @ExactlyOneOfRequired(GROUP_MODIFICATION) public List<JsonAddress> removeMembers;

  @ExactlyOneOfRequired(GROUP_MODIFICATION) public GroupMember updateRole;

  @Doc("note that only one of the access controls may be updated per request") @ExactlyOneOfRequired(GROUP_MODIFICATION) public GroupAccessControl updateAccessControl;

  @Doc("regenerate the group link password, invalidating the old one") @ExactlyOneOfRequired(GROUP_MODIFICATION) public boolean resetLink;

  @Doc("ENABLED to only allow admins to post messages, DISABLED to allow anyone to post") @ExactlyOneOfRequired(GROUP_MODIFICATION) public String announcements;

  @Override
  public GroupInfo run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UnknownGroupError, GroupVerificationError, InvalidRequestError {
    Account a = Common.getAccount(account);
    Manager m = Common.getManager(account);
    RecipientsTable recipientsTable = a.getRecipients();

    if (groupID.length() == 24) { // v1 group
      logger.warn("v1 group support is being removed https://gitlab.com/signald/signald/-/issues/224");
      List<Recipient> addMembersSignalServiceAddress = null;
      if (addMembers != null) {
        addMembersSignalServiceAddress = new ArrayList<>();

        for (JsonAddress member : addMembers) {
          addMembersSignalServiceAddress.add(Common.getRecipient(recipientsTable, member));
        }
      }
      byte[] rawGroupID;
      try {
        rawGroupID = Base64.decode(groupID);
      } catch (IOException e) {
        throw new UnknownGroupError();
      }
      io.finn.signald.storage.GroupInfo g;
      try {
        g = m.sendUpdateGroupMessage(rawGroupID, title, addMembersSignalServiceAddress, avatar);
      } catch (IOException | SQLException e) {
        throw new InternalError("error sending group update message", e);
      } catch (GroupNotFoundException | NotAGroupMemberException e) {
        throw new UnknownGroupError();
      }
      return new GroupInfo(g);
    } else {
      Groups groups = Common.getGroups(a);
      GroupsTable.Group group = Common.getGroup(a, groupID);

      List<Recipient> recipients;
      try {
        recipients = group.getMembers();
      } catch (IOException | SQLException e) {
        throw new InternalError("error looking up recipients", e);
      }

      GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, group);
      GroupChange.Actions.Builder change;
      try {
        if (title != null) {
          change = groupOperations.createModifyGroupTitle(title);
        } else if (description != null) {
          change = groupOperations.createModifyGroupDescription(description);
        } else if (avatar != null) {
          byte[] avatarBytes = Files.readAllBytes(new File(avatar).toPath());
          String cdnKey;
          try {
            cdnKey = groups.uploadNewAvatar(group.getSecretParams(), avatarBytes);
          } catch (VerificationFailedException e) {
            throw new InternalError("error uploading avatar", e);
          }
          change = GroupChange.Actions.newBuilder().setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar(cdnKey));
        } else if (addMembers != null && addMembers.size() > 0) {
          Set<GroupCandidate> candidates = new HashSet<>();
          for (JsonAddress member : addMembers) {
            Recipient recipient = recipientsTable.get(member);
            ProfileAndCredentialEntry profileAndCredentialEntry = m.getRecipientProfileKeyCredential(recipient);
            if (profileAndCredentialEntry == null) {
              logger.warn("failed to add group member with no profile");
              continue;
            }
            recipients.add(recipientsTable.get(profileAndCredentialEntry.getServiceAddress()));
            Optional<ProfileKeyCredential> profileKeyCredential = Optional.fromNullable(profileAndCredentialEntry.getProfileKeyCredential());
            UUID uuid = profileAndCredentialEntry.getServiceAddress().getAci().uuid();
            candidates.add(new GroupCandidate(uuid, profileKeyCredential));
          }
          change = groupOperations.createModifyGroupMembershipChange(candidates, a.getUUID());
        } else if (removeMembers != null && removeMembers.size() > 0) {
          Set<UUID> members = new HashSet<>();
          for (JsonAddress member : removeMembers) {
            Recipient recipient = recipientsTable.get(member);
            members.add(recipient.getUUID());
          }
          change = groupOperations.createRemoveMembersChange(members);
        } else if (updateRole != null) {
          UUID uuid = UUID.fromString(updateRole.uuid);
          Member.Role role;
          switch (updateRole.role) {
          case "ADMINISTRATOR":
            role = Member.Role.ADMINISTRATOR;
            break;
          case "DEFAULT":
            role = Member.Role.DEFAULT;
            break;
          default:
            throw new InvalidRequestError("unknown role requested");
          }
          change = groupOperations.createChangeMemberRole(uuid, role);
        } else if (updateAccessControl != null) {
          AccessControl.AccessRequired access;
          if (updateAccessControl.attributes != null) {
            if (updateAccessControl.members != null || updateAccessControl.link != null) {
              throw new InvalidRequestError("only one access control may be updated at once");
            }
            access = getAccessRequired(updateAccessControl.attributes);
          } else if (updateAccessControl.members != null) {
            if (updateAccessControl.link != null) {
              throw new InvalidRequestError("only one access control may be updated at once");
            }
            access = getAccessRequired(updateAccessControl.members);
          } else if (updateAccessControl.link != null) {
            access = getAccessRequired(updateAccessControl.link);
          } else {
            throw new InvalidRequestError("no known access control requested");
          }
          change = groupOperations.createChangeMembershipRights(access);
        } else if (resetLink) {
          change = groupOperations.createModifyGroupLinkPasswordChange(GroupLinkPassword.createNew().serialize());
        } else if (updateTimer > -1) {
          change = groupOperations.createModifyGroupTimerChange(updateTimer);
        } else if (announcements != null) {
          boolean announcementMode;
          switch (announcements) {
          case "ENABLED":
            announcementMode = true;
            break;
          case "DISABLED":
            announcementMode = false;
            break;
          default:
            throw new InvalidRequestError("unexpected value for key announcement: must be ENABLED or DISABLED");
          }
          change = groupOperations.createAnnouncementGroupChange(announcementMode);
        } else {
          throw new InvalidRequestError("no change requested");
        }

        Common.updateGroup(a, group, change);
      } catch (IOException | SQLException | ExecutionException | InterruptedException | InvalidInputException | TimeoutException e) {
        throw new InternalError("error updating group", e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
      return new GroupInfo(group.getJsonGroupV2Info());
    }
  }

  public AccessControl.AccessRequired getAccessRequired(String name) throws InvalidRequestError {
    switch (name) {
    case "ANY":
      return AccessControl.AccessRequired.ANY;
    case "MEMBER":
      return AccessControl.AccessRequired.MEMBER;
    case "ADMINISTRATOR":
      return AccessControl.AccessRequired.ADMINISTRATOR;
    case "UNSATISFIABLE":
      return AccessControl.AccessRequired.UNSATISFIABLE;
    default:
      throw new InvalidRequestError("invalid role: " + name);
    }
  }
}
