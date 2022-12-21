/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IGroupCredentialsTable;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.auth.AuthCredentialResponse;
import org.signal.libsignal.zkgroup.auth.AuthCredentialWithPniResponse;
import org.whispersystems.signalservice.api.push.ACI;

public class GroupCredentialsTable implements IGroupCredentialsTable {
  private static final String TABLE_NAME = "signald_group_credentials";

  private final ACI aci;

  public GroupCredentialsTable(ACI aci) { this.aci = aci; }

  @Override
  public Optional<AuthCredentialWithPniResponse> getCredential(int date) throws SQLException, InvalidInputException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", CREDENTIAL, TABLE_NAME, ACCOUNT_UUID, DATE);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setInt(2, date);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_credential", statement)) {
        return rows.next() ? Optional.of(new AuthCredentialWithPniResponse(rows.getBytes(CREDENTIAL))) : Optional.empty();
      }
    }
  }

  @Override
  public void setCredentials(HashMap<Long, AuthCredentialWithPniResponse> credentials) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                              // FIELDS
                              ACCOUNT_UUID, DATE, CREDENTIAL,
                              // ON CONFLICT
                              ACCOUNT_UUID, DATE,
                              // DO UPDATE SET
                              CREDENTIAL, CREDENTIAL);
    try (var statement = Database.getConn().prepareStatement(query)) {
      for (var entry : credentials.entrySet()) {
        statement.setObject(1, aci.uuid());
        statement.setLong(2, entry.getKey());
        statement.setBytes(3, entry.getValue().serialize());
        statement.addBatch();
      }
      Database.executeBatch(TABLE_NAME + "_set_credentials", statement);
    }
  }

  @Override
  public void deleteAccount(ACI aci) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
