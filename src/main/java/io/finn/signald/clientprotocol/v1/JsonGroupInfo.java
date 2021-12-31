/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.storage.GroupInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@Doc("information about a legacy group")
public class JsonGroupInfo {
  public String groupId;
  public List<JsonAddress> members;
  public String name;
  public String type;
  public long avatarId;

  JsonGroupInfo(SignalServiceGroup groupInfo, ACI aci) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    this.groupId = Base64.encodeBytes(groupInfo.getGroupId());
    if (groupInfo.getMembers().isPresent()) {
      this.members = new ArrayList<>();
      for (SignalServiceAddress member : groupInfo.getMembers().get()) {
        this.members.add(new JsonAddress(member));
      }
    }
    if (groupInfo.getName().isPresent()) {
      this.name = groupInfo.getName().get();
    } else {
      GroupInfo group = Common.getManager(aci).getGroup(groupInfo.getGroupId());
      if (group != null) {
        this.name = group.name;
      }
    }

    this.type = groupInfo.getType().toString();
  }

  public JsonGroupInfo(GroupInfo groupInfo) {
    this.groupId = Base64.encodeBytes(groupInfo.groupId);
    this.name = groupInfo.name;
    this.members = new ArrayList<>();
    for (SignalServiceAddress member : groupInfo.getMembers()) {
      this.members.add(new JsonAddress(member));
    }
    this.avatarId = groupInfo.getAvatarId();
  }
}
