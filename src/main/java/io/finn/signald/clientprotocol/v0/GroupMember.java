/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

@Deprecated(1641027661)
public class GroupMember {
  @ExampleValue(ExampleValue.REMOTE_UUID) public String uuid;
  @ExampleValue("\"DEFAULT\"") @Doc("possible values are: UNKNOWN, DEFAULT, ADMINISTRATOR and UNRECOGNIZED") public String role;
  @JsonProperty("joined_revision") public int joinedAtRevision;

  public GroupMember(@JsonProperty("uuid") String u, @JsonProperty("role") String r) {
    uuid = u;
    role = r;
  }

  public GroupMember(DecryptedMember d) {
    uuid = UuidUtil.fromByteStringOrUnknown(d.getUuid()).toString();
    role = d.getRole().name();
    joinedAtRevision = d.getJoinedAtRevision();
  }

  public GroupMember(DecryptedPendingMember d) {
    uuid = UuidUtil.fromByteStringOrUnknown(d.getUuid()).toString();
    role = d.getRole().name();
  }
}
