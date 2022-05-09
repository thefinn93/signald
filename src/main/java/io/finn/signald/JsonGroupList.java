/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.JsonGroupInfo;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Deprecated(1641027661)
class JsonGroupList {
  List<JsonGroupInfo> groups = new ArrayList<JsonGroupInfo>();
  List<JsonGroupV2Info> groupsv2;

  JsonGroupList(Manager m) throws SQLException { groupsv2 = m.getGroupsV2Info(); }
}
