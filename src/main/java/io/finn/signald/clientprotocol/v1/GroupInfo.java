/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;

@Doc("A generic type that is used when the group version is not known")
public class GroupInfo {
  public JsonGroupInfo v1;
  public JsonGroupV2Info v2;

  public GroupInfo(JsonGroupV2Info group) { v2 = group; }

  public GroupInfo(io.finn.signald.storage.GroupInfo group) { v1 = new JsonGroupInfo(group); }
}
