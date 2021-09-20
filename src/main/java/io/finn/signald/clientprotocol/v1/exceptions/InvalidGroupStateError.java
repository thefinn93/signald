/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;

public class InvalidGroupStateError extends ExceptionWrapper {
  public InvalidGroupStateError(InvalidGroupStateException e) { super(e); }
}
