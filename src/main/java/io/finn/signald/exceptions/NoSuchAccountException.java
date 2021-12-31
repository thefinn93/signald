/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

public class NoSuchAccountException extends Exception {
  public String account;
  public NoSuchAccountException(String account) {
    super("account not found");
    this.account = account;
  }
}
