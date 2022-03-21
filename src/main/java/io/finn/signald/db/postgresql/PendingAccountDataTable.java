/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IPendingAccountDataTable;
import java.sql.SQLException;

public class PendingAccountDataTable implements IPendingAccountDataTable {
  private static final String TABLE_NAME = "signald_pending_account_data";

  @Override
  public byte[] getBytes(String username, Key key) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", VALUE, TABLE_NAME, KEY, USERNAME);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, key.name());
      statement.setString(2, username);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_bytes", statement)) {
        return rows.next() ? rows.getBytes(VALUE) : null;
      }
    }
  }

  @Override
  public void set(String username, Key key, byte[] value) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                              // FIELDS
                              USERNAME, KEY, VALUE,
                              // ON CONFLICT
                              USERNAME, KEY,
                              // DO UPDATE SET
                              VALUE, VALUE);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, username);
      statement.setString(2, key.name());
      statement.setBytes(3, value);
      Database.executeUpdate(TABLE_NAME + "_set_bytes", statement);
    }
  }

  @Override
  public void clear(String username) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, USERNAME);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, username);
      Database.executeUpdate(TABLE_NAME + "_clear", statement);
    }
  }
}
