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
import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.AccessControl;

@Doc("group access control settings. Options for each controlled action are: UNKNOWN, ANY, MEMBER,"
     + " ADMINISTRATOR, UNSATISFIABLE and UNRECOGNIZED")
public class GroupAccessControl {
  @Doc("UNSATISFIABLE when the group link is disabled, ADMINISTRATOR when the group link is enabled"
       + " but an administrator must approve new members, ANY when the group link is enabled and no"
       + " approval is required")
  @ExampleValue("\"ANY\"")
  public String link;
  @Doc("who can edit group info") public String attributes;
  @Doc("who can add members") public String members;

  public GroupAccessControl(){};

  public GroupAccessControl(AccessControl a) {
    link = a.getAddFromInviteLink().toString();
    attributes = a.getAttributes().toString();
    members = a.getMembers().toString();
  }
}
