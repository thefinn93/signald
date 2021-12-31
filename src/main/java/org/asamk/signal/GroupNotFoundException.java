/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package org.asamk.signal;

import org.whispersystems.util.Base64;

public class GroupNotFoundException extends Exception {

  public GroupNotFoundException(String message) { super(message); }

  public GroupNotFoundException(byte[] groupId) { super("Group not found: " + Base64.encodeBytes(groupId)); }
}
