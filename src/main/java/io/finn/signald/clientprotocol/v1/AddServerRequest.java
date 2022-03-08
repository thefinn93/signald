/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.db.Database;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

@Doc("add a new server to connect to. Returns the new server's UUID.")
@ProtocolType("add_server")
public class AddServerRequest implements RequestType<String> {
  @Required public Server server;

  @Override
  public String run(Request request) throws InvalidProxyError, InternalError {
    if (server.uuid == null) {
      server.uuid = UUID.randomUUID();
    }
    try {
      Database.Get().ServersTable.create(server.getServer());
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (SQLException | IOException e) {
      throw new InternalError("error resolving account e164", e);
    }
    return server.uuid.toString();
  }
}
