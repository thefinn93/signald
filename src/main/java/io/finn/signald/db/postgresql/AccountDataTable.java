/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;

public class AccountDataTable implements IAccountDataTable {
  private static final String TABLE_NAME = "signald_account_data";

  @Override
  public byte[] getBytes(ACI aci, Key key) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", VALUE, TABLE_NAME, KEY, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, key.name());
      statement.setObject(2, aci.uuid());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_bytes", statement)) {
        return rows.next() ? rows.getBytes(VALUE) : null;
      }
    }
  }

  @Override
  public void set(ACI aci, Key key, byte[] value) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME, ACCOUNT_UUID, KEY, VALUE, ACCOUNT_UUID,
                              KEY, VALUE, VALUE);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setString(2, key.name());
      statement.setBytes(3, value);
      Database.executeUpdate(TABLE_NAME + "_set_bytes", statement);
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
