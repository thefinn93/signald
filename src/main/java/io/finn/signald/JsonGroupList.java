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

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonGroupInfo;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.GroupInfo;

import java.util.ArrayList;
import java.util.List;

class JsonGroupList {
  List<JsonGroupInfo> groups = new ArrayList<JsonGroupInfo>();
  List<JsonGroupV2Info> groupsv2;

  JsonGroupList(Manager m) {
    for (GroupInfo group : m.getV1Groups()) {
      if (group != null) {
        this.groups.add(new JsonGroupInfo(group, m));
      }
    }

    groupsv2 = m.getGroupsV2Info();
  }
}
