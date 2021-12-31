/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import java.util.ArrayList;
import java.util.List;

public class GroupList {
  public List<JsonGroupV2Info> groups;
  public List<JsonGroupInfo> legacyGroups;

  public GroupList() { groups = new ArrayList<>(); }

  public void add(JsonGroupV2Info g) { groups.add(g); }

  public void add(JsonGroupInfo g) {
    if (legacyGroups == null) {
      legacyGroups = new ArrayList<>();
    }
    legacyGroups.add(g);
  }
}
