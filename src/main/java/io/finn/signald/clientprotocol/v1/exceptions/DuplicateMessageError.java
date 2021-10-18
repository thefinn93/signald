/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import org.whispersystems.libsignal.DuplicateMessageException;

public class DuplicateMessageError extends ExceptionWrapper {
  public DuplicateMessageError(DuplicateMessageException e) { super(e); }
}
