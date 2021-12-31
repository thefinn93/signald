/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

import java.util.UUID;

public class ServerNotFoundException extends Exception {
  private UUID server;
  public ServerNotFoundException(UUID server) {
    super("server not found: " + (server == null ? "null" : server.toString()));
    this.server = server;
  }

  public UUID getServer() { return server; }
}
