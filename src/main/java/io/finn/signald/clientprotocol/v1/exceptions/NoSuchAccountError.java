/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class NoSuchAccountError extends ExceptionWrapper {
  public String account;
  public NoSuchAccountError(io.finn.signald.exceptions.NoSuchAccountException e) {
    super(e.getMessage());
    account = e.account;
  }

  public NoSuchAccountError(String account) {
    super("account not found");
    this.account = account;
  }
}
