/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class NoSendPermissionError extends ExceptionWrapper {
  public NoSendPermissionError() { super("only admins are allowed to send to this group"); }
}
