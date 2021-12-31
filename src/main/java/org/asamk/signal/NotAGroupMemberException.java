/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package org.asamk.signal;

import org.whispersystems.util.Base64;

public class NotAGroupMemberException extends Exception {

  public NotAGroupMemberException(String message) { super(message); }

  public NotAGroupMemberException(byte[] groupId, String groupName) { super("User is not a member in group: " + groupName + " (" + Base64.encodeBytes(groupId) + ")"); }
}
