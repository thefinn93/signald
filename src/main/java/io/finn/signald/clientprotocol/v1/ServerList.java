/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.db.ServersTable;
import java.util.List;
import java.util.stream.Collectors;

public class ServerList {
  public List<Server> servers;

  public ServerList(List<ServersTable.Server> servers) { this.servers = servers.stream().map(Server::new).collect(Collectors.toList()); }
}
