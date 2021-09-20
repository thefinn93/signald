/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.db.ServersTable;
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
      ServersTable.create(server.getServer());
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (SQLException | IOException e) {
      throw new InternalError("error resolving account e164", e);
    }
    return server.uuid.toString();
  }
}
