/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.nio.ByteBuffer;
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
    LAST_ACCOUNT_REPAIR, // fixes to historical signald bugs (see ../AccountRepair.java)
    STORAGE_KEY,
    STORAGE_MANIFEST_VERSION
  }

  public static byte[] getBytes(ACI aci, Key key) throws SQLException {
    var query = "SELECT " + VALUE + " FROM " + TABLE_NAME + " WHERE " + KEY + " = ? AND " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, key.name());
      statement.setString(2, aci.toString());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_bytes", statement)) {
        return rows.next() ? rows.getBytes(VALUE) : null;
      }
    }
  }

  public static int getInt(ACI aci, Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? ByteBuffer.wrap(bytes).getInt() : -1;
  }

  public static long getLong(ACI aci, Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? ByteBuffer.wrap(bytes).getLong() : -1;
  }

  public static String getString(ACI aci, Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? new String(bytes) : null;
  }

  public static Boolean getBoolean(ACI aci, Key key) throws SQLException {
    var val = getInt(aci, key);
    return val > -1 ? (val == 1) : null;
  }

  public static void set(ACI aci, Key key, byte[] value) throws SQLException {
    var query = "INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + KEY + "," + VALUE + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," + KEY + ") DO UPDATE SET " +
                VALUE + " = excluded." + VALUE;
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, key.name());
      statement.setBytes(3, value);
      Database.executeUpdate(TABLE_NAME + "_set_bytes", statement);
    }
  }

  public static void set(ACI aci, Key key, int value) throws SQLException { set(aci, key, ByteBuffer.allocate(4).putInt(value).array()); }

  public static void set(ACI aci, Key key, long value) throws SQLException { set(aci, key, ByteBuffer.allocate(8).putLong(value).array()); }

  public static void set(ACI aci, Key key, String value) throws SQLException { set(aci, key, value != null ? value.getBytes() : new byte[] {}); }

  public static void set(ACI aci, Key key, boolean value) throws SQLException { set(aci, key, value ? 1 : 0); }

  public static void deleteAccount(UUID uuid) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, uuid.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
