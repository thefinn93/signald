/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.AccessControl;

@Doc("group access control settings. Options for each controlled action are: UNKNOWN, ANY, MEMBER,"
     + " ADMINISTRATOR, UNSATISFIABLE and UNRECOGNIZED")
@Deprecated(1641027661)
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
