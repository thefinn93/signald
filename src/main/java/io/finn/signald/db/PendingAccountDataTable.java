/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PendingAccountDataTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "pending_account_data";

  private static final String USERNAME = "username";
  private static final String KEY = "key";
  private static final String VALUE = "value";

  public enum Key { OWN_IDENTITY_KEY_PAIR, LOCAL_REGISTRATION_ID, SERVER_UUID, PASSWORD }

  public static byte[] getBytes(String username, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + USERNAME + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, username);
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    byte[] result = rows.getBytes(VALUE);
    rows.close();
    return result;
  }

  public static String getString(String username, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + USERNAME + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, username);
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    String result = rows.getString(VALUE);
    rows.close();
    return result;
  }

  public static int getInt(String username, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + USERNAME + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, username);
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    int result = rows.getInt(VALUE);
    rows.close();
    return result;
  }

  public static void set(String username, Key key, byte[] value) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + USERNAME + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" +
                                                                      USERNAME + "," + KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, username);
    statement.setString(2, key.name());
    statement.setBytes(3, value);
    statement.executeUpdate();
  }

  public static void set(String username, Key key, String value) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + USERNAME + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" +
                                                                      USERNAME + "," + KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, username);
    statement.setString(2, key.name());
    statement.setString(3, value);
    statement.executeUpdate();
  }

  public static void set(String username, Key key, int value) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + USERNAME + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" +
                                                                      USERNAME + "," + KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, username);
    statement.setString(2, key.name());
    statement.setInt(3, value);
    statement.executeUpdate();
  }

  public static void clear(String username) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + USERNAME + " = ?");
    statement.setString(1, username);
    statement.executeUpdate();
  }
}
