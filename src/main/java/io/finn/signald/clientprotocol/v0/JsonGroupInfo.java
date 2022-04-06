/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.Manager;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.GroupInfo;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonGroupInfo {
  public String groupId;
  public List<JsonAddress> members;
  public String name;
  public String type;
  public long avatarId;

  JsonGroupInfo(SignalServiceGroup groupInfo, ACI aci)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
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
      GroupInfo group = Manager.get(aci).getGroup(groupInfo.getGroupId());
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
