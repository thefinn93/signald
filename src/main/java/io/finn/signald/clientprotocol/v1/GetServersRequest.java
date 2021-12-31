/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.ServersTable;
import java.sql.SQLException;

@ProtocolType("get_servers")
public class GetServersRequest implements RequestType<ServerList> {
  @Override
  public ServerList run(Request request) throws InternalError {
    try {
      return new ServerList(ServersTable.allServers());
    } catch (SQLException e) {
      throw new InternalError("error listing servers", e);
    }
  }
}
