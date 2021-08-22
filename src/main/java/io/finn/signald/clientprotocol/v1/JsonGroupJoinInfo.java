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
