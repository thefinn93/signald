/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.IGroupCredentialsTable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;

public class GroupCredentialsTable implements IGroupCredentialsTable {
  private static final String TABLE_NAME = "group_credentials";

  private final ACI aci;

  public GroupCredentialsTable(ACI aci) { this.aci = aci; }

  @Override
  public Optional<AuthCredentialResponse> getCredential(int date) throws SQLException, InvalidInputException {
    var query = "SELECT " + CREDENTIAL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DATE + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setInt(2, date);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_credential", statement)) {
        return rows.next() ? Optional.of(new AuthCredentialResponse(rows.getBytes(CREDENTIAL))) : Optional.absent();
      }
    }
  }

  @Override
  public void setCredentials(HashMap<Integer, AuthCredentialResponse> credentials) throws SQLException {
    var query = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" + ACCOUNT_UUID + "," + DATE + "," + CREDENTIAL + ") VALUES (?, ?, ?)";
    try (var statement = Database.getConn().prepareStatement(query)) {
      for (Map.Entry<Integer, AuthCredentialResponse> entry : credentials.entrySet()) {
        statement.setString(1, aci.toString());
        statement.setInt(2, entry.getKey());
        statement.setBytes(3, entry.getValue().serialize());
        statement.addBatch();
      }
      Database.executeBatch(TABLE_NAME + "_set_credentials", statement);
    }
  }

  @Override
  public void deleteAccount(UUID uuid) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, uuid.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
