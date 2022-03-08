/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.libsignal.state.PreKeyStore;

public interface IPreKeysTable extends PreKeyStore {
  String ACCOUNT_UUID = "account_uuid";
  String ID = "id";
  String RECORD = "record";

  void deleteAccount(UUID uuid) throws SQLException;
}
