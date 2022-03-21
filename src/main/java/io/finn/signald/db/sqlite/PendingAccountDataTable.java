/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.IPendingAccountDataTable;
import java.sql.SQLException;

public class PendingAccountDataTable implements IPendingAccountDataTable {

  private static final String TABLE_NAME = "pending_account_data";

  @Override
  public byte[] getBytes(String username, Key key) throws SQLException {
    var query = "SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + USERNAME + " = ?";
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
    var query = "INSERT INTO " + TABLE_NAME + "(" + USERNAME + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + USERNAME + "," + KEY + ") DO UPDATE SET " + VALUE +
                " = excluded." + VALUE;
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, username);
      statement.setString(2, key.name());
      statement.setBytes(3, value);
      Database.executeUpdate(TABLE_NAME + "_set_bytes", statement);
    }
  }

  @Override
  public void clear(String username) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + USERNAME + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, username);
      Database.executeUpdate(TABLE_NAME + "_clear", statement);
    }
  }
}
