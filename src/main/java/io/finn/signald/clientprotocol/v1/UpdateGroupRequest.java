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
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.Member;
import org.whispersystems.signalservice.api.groupsv2.GroupCandidate;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;

@ProtocolType("update_group")
@ErrorDoc(error = AuthorizationFailedError.class, doc = AuthorizationFailedError.DEFAULT_ERROR_DOC)
@ErrorDoc(error = GroupPatchNotAcceptedError.class, doc = "Caused when server rejects the group update, e.g. trying to add a user that's already in the group")
@Doc("modify a group. Note that only one modification action may be performed at once")
public class UpdateGroupRequest implements RequestType<GroupInfo> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The identifier of the account to interact with") @Required public String account;

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
  public GroupInfo run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UnknownGroupError, GroupVerificationError,
                                               InvalidRequestError, AuthorizationFailedError, UnregisteredUserError, SQLError, GroupPatchNotAcceptedError, UnsupportedGroupError {
    Account a = Common.getAccount(account);
    var recipientsTable = a.getDB().RecipientsTable;

    if (groupID.length() == 24) { // v1 group
      throw new UnsupportedGroupError();
    }

    Groups groups = Common.getGroups(a);
    var group = Common.getGroup(a, groupID);

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
        } catch (InvalidInputException | IOException e) {
          throw new InternalError("error uploading new avatar: ", e);
        } catch (SQLException e) {
          throw new SQLError(e);
        }
        change = GroupChange.Actions.newBuilder().setModifyAvatar(GroupChange.Actions.ModifyAvatarAction.newBuilder().setAvatar(cdnKey));
      } else if (addMembers != null && addMembers.size() > 0) {
        Set<GroupCandidate> candidates = new HashSet<>();
        for (JsonAddress member : addMembers) {
          Recipient recipient = recipientsTable.get(member);
          ExpiringProfileKeyCredential expiringProfileKeyCredential = a.getDB().ProfileKeysTable.getExpiringProfileKeyCredential(recipient);
          recipients.add(recipientsTable.get(recipient.getAddress()));
          UUID uuid = recipient.getUUID();
          candidates.add(new GroupCandidate(uuid, Optional.ofNullable(expiringProfileKeyCredential)));
        }
        change = groupOperations.createModifyGroupMembershipChange(candidates, Set.of(), a.getUUID());
      } else if (removeMembers != null && removeMembers.size() > 0) {
        Set<UUID> members = new HashSet<>();
        for (JsonAddress member : removeMembers) {
          Recipient recipient = recipientsTable.get(member);
          members.add(recipient.getUUID());
        }
        change = groupOperations.createRemoveMembersChange(members, false, List.of());
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
        if (updateAccessControl.attributes != null) {
          if (updateAccessControl.members != null || updateAccessControl.link != null) {
            throw new InvalidRequestError("only one access control may be updated at once");
          }
          change = groupOperations.createChangeAttributesRights(getAccessRequired(updateAccessControl.attributes));
        } else if (updateAccessControl.members != null) {
          if (updateAccessControl.link != null) {
            throw new InvalidRequestError("only one access control may be updated at once");
          }
          change = groupOperations.createChangeMembershipRights(getAccessRequired(updateAccessControl.members));
        } else if (updateAccessControl.link != null) {
          final AccessControl.AccessRequired access = getAccessRequired(updateAccessControl.link);
          if (access != AccessControl.AccessRequired.ADMINISTRATOR && access != AccessControl.AccessRequired.ANY && access != AccessControl.AccessRequired.UNSATISFIABLE) {
            throw new InvalidRequestError("unexpected value for key updateAccessControl.link: must be ADMINISTRATOR, ANY, or UNSATISFIABLE");
          }

          change = groupOperations.createChangeJoinByLinkRights(access);
          if (access != AccessControl.AccessRequired.UNSATISFIABLE) {
            if (group.getDecryptedGroup().getInviteLinkPassword().isEmpty()) {
              logger.debug("First time enabling group links for group and password empty, generating");
              change = groupOperations.createModifyGroupLinkPasswordAndRightsChange(GroupLinkPassword.createNew().serialize(), access);
            }
          }
        } else {
          throw new InvalidRequestError("no known access control requested");
        }
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
    } catch (IOException | SQLException | InvalidInputException e) {
      throw new InternalError("error updating group: ", e);
    }

    Common.updateGroup(a, group, change);
    return new GroupInfo(group.getJsonGroupV2Info());
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
