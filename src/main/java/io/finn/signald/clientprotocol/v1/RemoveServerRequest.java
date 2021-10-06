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

import io.finn.signald.Empty;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.ServersTable;
import java.sql.SQLException;
import java.util.UUID;

@ProtocolType("delete_server")
public class RemoveServerRequest implements RequestType<Empty> {
  public String uuid;

  @Override
  public Empty run(Request request) throws InternalError {
    try {
      ServersTable.delete(UUID.fromString(uuid));
    } catch (SQLException e) {
      throw new InternalError("error removing server", e);
    }
    return new Empty();
  }
}
