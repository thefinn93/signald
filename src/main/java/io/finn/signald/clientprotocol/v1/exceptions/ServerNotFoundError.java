/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class ServerNotFoundError extends ExceptionWrapper {
  public String uuid;

  public ServerNotFoundError(io.finn.signald.exceptions.ServerNotFoundException e) {
    super(e.getMessage());
    uuid = e.getServer() == null ? null : e.getServer().toString();
  }
}
