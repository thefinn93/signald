/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;

public class GroupNotActiveError extends ExceptionWrapper {
  public GroupNotActiveError(GroupLinkNotActiveException e) { super(e); }
}
