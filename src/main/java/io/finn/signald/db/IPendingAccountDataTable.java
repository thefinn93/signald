/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.sql.SQLException;

public interface IPendingAccountDataTable {
  String USERNAME = "username";
  String KEY = "key";
  String VALUE = "value";

  enum Key { OWN_IDENTITY_KEY_PAIR, LOCAL_REGISTRATION_ID, SERVER_UUID, PASSWORD }

  byte[] getBytes(String username, IPendingAccountDataTable.Key key) throws SQLException;
  String getString(String username, IPendingAccountDataTable.Key key) throws SQLException;
  int getInt(String username, IPendingAccountDataTable.Key key) throws SQLException;
  void set(String username, IPendingAccountDataTable.Key key, byte[] value) throws SQLException;
  void set(String username, IPendingAccountDataTable.Key key, String value) throws SQLException;
  void set(String username, IPendingAccountDataTable.Key key, int value) throws SQLException;
  void clear(String username) throws SQLException;
}
