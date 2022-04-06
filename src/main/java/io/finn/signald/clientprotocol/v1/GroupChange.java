package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Groups;
import io.finn.signald.annotations.Doc;
import io.finn.signald.db.IGroupsTable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.util.UuidUtil;

@Doc("Represents a group change made by a user. This can also represent request link invites. Only the fields "
     + "relevant to the group change performed will be set. Note that in signald, group changes are currently only "
     + "received from incoming messages from a message subscription.")
public class GroupChange {
  @Doc("The user that made the change.") public JsonAddress editor;
  @Doc("The group revision that this change brings the group to.") public Integer revision;
  @JsonProperty("new_members")
  @Doc("Represents users have been added to the group. This can be from group members adding users, or a users joining via a group link that required no approval.")
  public List<GroupMember> newMembers;
  @JsonProperty("delete_members")
  @Doc("Represents users that have been removed from the group. This can be from admins removing users, or users choosing to leave the group")
  public List<JsonAddress> deleteMembers;
  @JsonProperty("modify_member_roles") @Doc("Represents users with their new, modified role.") public List<GroupMember> modifyMemberRoles;
  @JsonProperty("modified_profile_keys")
  @Doc("Represents users that have rotated their profile key. Note that signald "
       + "currently does not expose profile keys to clients. The joined revision property will always be 0 in this "
       + "list.")
  public List<GroupMember> modifiedProfileKeys;
  @JsonProperty("new_pending_members") @Doc("Represents a user that has been invited to the group by another user.") public List<GroupPendingMember> newPendingMembers;
  @JsonProperty("delete_pending_members") public List<JsonAddress> deletePendingMembers;
  @JsonProperty("promote_pending_members") public List<GroupMember> promotePendingMembers;
  @JsonProperty("new_banned_members") public List<BannedGroupMember> newBannedMembers;
  @JsonProperty("new_unbanned_members") public List<BannedGroupMember> newUnbannedMembers;
  @JsonProperty("new_title") public String newTitle;
  @JsonProperty("new_avatar") @Doc("Whether this group change changed the avatar.") public Boolean newAvatar;
  @JsonProperty("new_timer") @Doc("New disappearing messages timer value.") public Integer newTimer;
  @JsonProperty("new_access_control")
  @Doc("If not null, then this group change modified one of the access controls. Some of the properties in here will be null.")
  public GroupAccessControl newAccessControl;
  @JsonProperty("new_requesting_members")
  @Doc("Represents users that have requested to join the group via the group "
       + "link. Note that members requesting to join might not necessarily have the list of users in the group, so "
       + "they won't be able to send a peer-to-peer group update message to inform users of their request to join. "
       + "Other users in the group may inform us that the revision has increased, but the members requesting access "
       + "will have to be obtained from the server instead (which signald will handle). For now, a get_group "
       + "request has to be made to get the users that have requested to join the group.")
  public List<GroupRequestingMember> newRequestingMembers;
  @JsonProperty("delete_requesting_members") public List<JsonAddress> deleteRequestingMembers;
  @JsonProperty("promote_requesting_members") public List<GroupMember> promoteRequestingMembers;
  @JsonProperty("new_invite_link_password") @Doc("Whether this group change involved resetting the group invite link.") public Boolean newInviteLinkPassword;
  @JsonProperty("new_description") public String newDescription;
  @JsonProperty("new_is_announcement_group")
  @Doc("Whether this change affected the announcement group setting. Possible values are UNKNOWN, ENABLED or DISABLED")
  public String newIsAnnouncementGroup;

  public static GroupChange fromBytes(Groups groups, IGroupsTable.IGroup group, byte[] signedGroupChange)
      throws InvalidGroupStateException, IOException, VerificationFailedException {
    final org.signal.storageservice.protos.groups.GroupChange protoGroupChange = org.signal.storageservice.protos.groups.GroupChange.parseFrom(signedGroupChange);

    final Optional<DecryptedGroupChange> changeOptional = groups.decryptChange(group, protoGroupChange, true);
    if (changeOptional.isEmpty()) {
      throw new IOException("failed to get decrypted group change");
    }
    return new GroupChange(changeOptional.get());
  }

  public GroupChange(DecryptedGroupChange change) {
    // The order of these fields correspond to the upstream protobuf order for DecryptedGroupChange.
    editor = new JsonAddress(UuidUtil.fromByteStringOrUnknown(change.getEditor()));
    revision = change.getRevision();
    if (!change.getNewMembersList().isEmpty()) {
      newMembers = change.getNewMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
    if (!change.getDeleteMembersList().isEmpty()) {
      deleteMembers = change.getDeleteMembersList().stream().map(UuidUtil::fromByteStringOrUnknown).map(JsonAddress::new).collect(Collectors.toList());
    }
    if (!change.getModifyMemberRolesList().isEmpty()) {
      modifyMemberRoles = change.getModifyMemberRolesList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
    if (!change.getModifiedProfileKeysList().isEmpty()) {
      modifiedProfileKeys = change.getModifiedProfileKeysList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
    if (!change.getNewPendingMembersList().isEmpty()) {
      newPendingMembers = change.getNewPendingMembersList().stream().map(GroupPendingMember::new).collect(Collectors.toList());
    }
    if (!change.getDeletePendingMembersList().isEmpty()) {
      deletePendingMembers =
          change.getDeletePendingMembersList().stream().map(m -> UuidUtil.fromByteStringOrUnknown(m.getUuid())).map(JsonAddress::new).collect(Collectors.toList());
    }
    if (!change.getPromotePendingMembersList().isEmpty()) {
      promotePendingMembers = change.getPromotePendingMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
    if (!change.getNewBannedMembersList().isEmpty()) {
      newBannedMembers = change.getNewBannedMembersList().stream().map(BannedGroupMember::new).collect(Collectors.toList());
    }
    if (!change.getDeleteBannedMembersList().isEmpty()) {
      newUnbannedMembers = change.getDeleteBannedMembersList().stream().map(BannedGroupMember::new).collect(Collectors.toList());
    }
    if (change.hasNewTitle()) {
      newTitle = change.getNewTitle().getValue();
    }
    if (change.hasNewAvatar()) {
      newAvatar = true;
    }
    if (change.hasNewTimer()) {
      newTimer = change.getNewTimer().getDuration();
    }
    if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN || change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN ||
        change.getNewInviteLinkAccess() != AccessControl.AccessRequired.UNKNOWN) {
      newAccessControl = new GroupAccessControl();
      if (change.getNewAttributeAccess() != AccessControl.AccessRequired.UNKNOWN) {
        newAccessControl.attributes = change.getNewAttributeAccess().name();
      }
      if (change.getNewMemberAccess() != AccessControl.AccessRequired.UNKNOWN) {
        newAccessControl.members = change.getNewMemberAccess().name();
      }
      if (change.getNewInviteLinkAccess() != AccessControl.AccessRequired.UNKNOWN) {
        newAccessControl.link = change.getNewInviteLinkAccess().name();
      }
    }
    if (!change.getNewRequestingMembersList().isEmpty()) {
      newRequestingMembers = change.getNewRequestingMembersList().stream().map(GroupRequestingMember::new).collect(Collectors.toList());
    }
    if (!change.getDeleteRequestingMembersList().isEmpty()) {
      deleteRequestingMembers = change.getDeleteRequestingMembersList().stream().map(UuidUtil::fromByteStringOrUnknown).map(JsonAddress::new).collect(Collectors.toList());
    }
    if (!change.getPromoteRequestingMembersList().isEmpty()) {
      promoteRequestingMembers = change.getPromoteRequestingMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
    if (!change.getNewInviteLinkPassword().isEmpty()) {
      newInviteLinkPassword = true;
    }
    if (change.hasNewDescription()) {
      newDescription = change.getNewDescription().getValue();
    }
    if (change.getNewIsAnnouncementGroup() != EnabledState.UNKNOWN) {
      newIsAnnouncementGroup = change.getNewIsAnnouncementGroup().name();
    }
  }
}
