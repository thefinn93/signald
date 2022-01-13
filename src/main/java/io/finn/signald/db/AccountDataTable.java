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
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.ACI;

public class AccountDataTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "account_data";

  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String KEY = "key";
  private static final String VALUE = "value";

  public enum Key {
    OWN_IDENTITY_KEY_PAIR,
    LOCAL_REGISTRATION_ID,
    LAST_PRE_KEY_REFRESH,
    DEVICE_NAME,
    SENDER_CERTIFICATE,
    SENDER_CERTIFICATE_REFRESH_TIME,
    MULTI_DEVICE,
    DEVICE_ID,
    PASSWORD,
    LAST_ACCOUNT_REFRESH, // server account updates when new device properties are added
    PRE_KEY_ID_OFFSET,
    NEXT_SIGNED_PRE_KEY_ID,
    LAST_ACCOUNT_REPAIR // fixes to historical signald bugs (see ../AccountRepair.java)
  }

  public static byte[] getBytes(ACI aci, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, aci.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    byte[] result = rows.getBytes(VALUE);
    rows.close();
    return result;
  }

  public static int getInt(ACI aci, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, aci.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    int result = rows.getInt(VALUE);
    rows.close();
    return result;
  }

  public static long getLong(ACI aci, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, aci.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    long result = rows.getLong(VALUE);
    rows.close();
    return result;
  }

  public static String getString(ACI aci, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, aci.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    String result = rows.getString(VALUE);
    rows.close();
    return result;
  }

  public static Boolean getBoolean(ACI aci, Key key) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?");
    statement.setString(1, key.name());
    statement.setString(2, aci.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    Boolean result = rows.getBoolean(VALUE);
    rows.close();
    return result;
  }

  public static void set(ACI aci, Key key, byte[] value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, aci.toString());
    statement.setString(2, key.name());
    statement.setBytes(3, value);
    statement.executeUpdate();
  }

  public static void set(ACI aci, Key key, int value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, aci.toString());
    statement.setString(2, key.name());
    statement.setInt(3, value);
    statement.executeUpdate();
  }

  public static void set(ACI aci, Key key, long value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, aci.toString());
    statement.setString(2, key.name());
    statement.setLong(3, value);
    statement.executeUpdate();
  }

  public static void set(ACI aci, Key key, String value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, aci.toString());
    statement.setString(2, key.name());
    statement.setString(3, value);
    statement.executeUpdate();
  }

  public static void set(ACI aci, Key key, boolean value) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                            KEY + ") DO UPDATE SET " + VALUE + " = excluded." + VALUE);
    statement.setString(1, aci.toString());
    statement.setString(2, key.name());
    statement.setBoolean(3, value);
    statement.executeUpdate();
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
