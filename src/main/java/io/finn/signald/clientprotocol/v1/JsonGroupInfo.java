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
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.storage.GroupInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@Doc("information about a legacy group")
public class JsonGroupInfo {
  public String groupId;
  public List<JsonAddress> members;
  public String name;
  public String type;
  public long avatarId;

  JsonGroupInfo(SignalServiceGroup groupInfo, UUID accountUUID) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
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
      GroupInfo group = Common.getManager(accountUUID).getGroup(groupInfo.getGroupId());
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
