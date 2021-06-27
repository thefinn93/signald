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
