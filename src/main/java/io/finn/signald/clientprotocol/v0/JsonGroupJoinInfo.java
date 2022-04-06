/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.util.GroupsUtil;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonGroupJoinInfo {
  @ExampleValue(ExampleValue.GROUP_ID) public String groupID;
  @ExampleValue(ExampleValue.GROUP_TITLE) public String title;
  @ExampleValue("3") public int memberCount;
  public int addFromInviteLink;
  @ExampleValue("5") public int revision;
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
