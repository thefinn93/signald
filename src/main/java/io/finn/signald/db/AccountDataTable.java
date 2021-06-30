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

package io.finn.signald.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountDataTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "account_data";

  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String KEY = "key";
  private static final String VALUE = "value";

  public enum Key { OWN_IDENTITY_KEY_PAIR, LOCAL_REGISTRATION_ID, LAST_PRE_KEY_REFRESH, DEVICE_NAME, SENDER_CERTIFICATE, SENDER_CERTIFICATE_REFRESH_TIME }

  public static byte[] getBytes(UUID uuid, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, uuid.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    byte[] result = rows.getBytes(VALUE);
    rows.close();
    return result;
  }

  public static int getInt(UUID uuid, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, uuid.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    int result = rows.getInt(VALUE);
    rows.close();
    return result;
  }

  public static long getLong(UUID uuid, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, uuid.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    long result = rows.getLong(VALUE);
    rows.close();
    return result;
  }

  public static String getString(UUID uuid, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, uuid.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    String result = rows.getString(VALUE);
    rows.close();
    return result;
  }

  public static void set(UUID uuid, Key key, byte[] value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, uuid.toString());
    statement.setString(2, key.name());
    statement.setBytes(3, value);
    statement.executeUpdate();
  }

  public static void set(UUID uuid, Key key, int value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, uuid.toString());
    statement.setString(2, key.name());
    statement.setInt(3, value);
    statement.executeUpdate();
  }

  public static void set(UUID uuid, Key key, long value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, uuid.toString());
    statement.setString(2, key.name());
    statement.setLong(3, value);
    statement.executeUpdate();
  }

  public static void set(UUID uuid, Key key, String value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, uuid.toString());
    statement.setString(2, key.name());
    statement.setString(3, value);
    statement.executeUpdate();
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
