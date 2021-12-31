/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
