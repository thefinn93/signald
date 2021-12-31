/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.util.Base64;

public class JsonGroupJoinInfo {
  @ExampleValue(ExampleValue.GROUP_ID) public String groupID;
  @ExampleValue(ExampleValue.GROUP_TITLE) public String title;
  @ExampleValue(ExampleValue.GROUP_DESCRIPTION) public String description;
  @ExampleValue("3") public int memberCount;

  @Doc("The access level required in order to join the group from the invite link, as an "
       + "AccessControl.AccessRequired enum from the upstream Signal groups.proto file. This is UNSATISFIABLE (4) "
       + "when the group link is disabled; ADMINISTRATOR (3) when the group link is enabled, but an administrator must "
       + "approve new members; and ANY (1) when the group link is enabled and no approval is required. See the"
       + "GroupAccessControl structure and the upstream enum ordinals.")
  public int addFromInviteLink;

  @Doc("The Group V2 revision. This is incremented by clients whenever they update group information, and it is often "
       + "used by clients to determine if the local group state is out-of-date with the server's revision.")
  @ExampleValue("5")
  public int revision;

  @Doc("Whether the account is waiting for admin approval in order to be added to the group.") public boolean pendingAdminApproval;

  public JsonGroupJoinInfo(DecryptedGroupJoinInfo i, GroupMasterKey masterKey) {
    groupID = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(masterKey).serialize());
    title = i.getTitle();
    description = i.getDescription();
    memberCount = i.getMemberCount();
    addFromInviteLink = i.getAddFromInviteLinkValue();
    revision = i.getRevision();
    pendingAdminApproval = i.getPendingAdminApproval();
  }
}
