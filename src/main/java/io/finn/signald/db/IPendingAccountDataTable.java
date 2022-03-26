/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.nio.ByteBuffer;
import java.sql.SQLException;

public interface IPendingAccountDataTable {
  String USERNAME = "username";
  String KEY = "key";
  String VALUE = "value";

  enum Key { ACI_IDENTITY_KEY_PAIR, PNI_IDENTITY_KEY_PAIR, LOCAL_REGISTRATION_ID, SERVER_UUID, PASSWORD }

  byte[] getBytes(String username, IPendingAccountDataTable.Key key) throws SQLException;
  default String getString(String username, IPendingAccountDataTable.Key key) throws SQLException {
    var bytes = getBytes(username, key);
    return bytes != null ? new String(bytes) : null;
  }

  // Default implementations for getting values
  default int getInt(String username, IPendingAccountDataTable.Key key) throws SQLException {
    var bytes = getBytes(username, key);
    return bytes != null ? ByteBuffer.wrap(bytes).getInt() : -1;
  }
  void set(String username, IPendingAccountDataTable.Key key, byte[] value) throws SQLException;

  default void set(String username, IPendingAccountDataTable.Key key, int value) throws SQLException { set(username, key, ByteBuffer.allocate(4).putInt(value).array()); }
  default void set(String username, IPendingAccountDataTable.Key key, String value) throws SQLException { set(username, key, value != null ? value.getBytes() : new byte[] {}); }

  void clear(String username) throws SQLException;
}
