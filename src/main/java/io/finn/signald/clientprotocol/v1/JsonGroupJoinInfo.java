/*
 * Copyright (C) 2020 Finn Herzfeld
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

import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.util.Base64;

public class JsonGroupJoinInfo {
  public String groupID;
  public String title;
  public int memberCount;
  public int addFromInviteLink;
  public int revision;
  public boolean pendingAdminApproval;

  public JsonGroupJoinInfo(DecryptedGroupJoinInfo i, GroupMasterKey masterKey) {
    groupID = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(masterKey).serialize());
    title = i.getTitle();
    memberCount = i.getMemberCount();
    addFromInviteLink = i.getAddFromInviteLinkValue();
    revision = i.getRevision();
    pendingAdminApproval = i.getPendingAdminApproval();
  }
}
