/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class AccountLockedError extends ExceptionWrapper {
  public final String more = "see https://gitlab.com/signald/signald/-/issues/47";
  public AccountLockedError() { super("account is locked with a PIN"); }
}
