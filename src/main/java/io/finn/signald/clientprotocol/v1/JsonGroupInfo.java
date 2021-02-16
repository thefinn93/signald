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

import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.storage.GroupInfo;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JsonGroupInfo {
  public String groupId;
  public List<JsonAddress> members;
  public String name;
  public String type;
  public long avatarId;

  JsonGroupInfo(SignalServiceGroup groupInfo, String username) throws IOException, NoSuchAccountException, SQLException {
    Manager manager = Manager.get(username);
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
      GroupInfo group = manager.getGroup(groupInfo.getGroupId());
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
