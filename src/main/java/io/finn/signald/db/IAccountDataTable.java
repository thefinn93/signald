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
import org.whispersystems.signalservice.api.push.ACI;

public interface IAccountDataTable {
  String ACCOUNT_UUID = "account_uuid";
  String KEY = "key";
  String VALUE = "value";

  byte[] getBytes(ACI aci, IAccountDataTable.Key key) throws SQLException;
  void set(ACI aci, IAccountDataTable.Key key, byte[] value) throws SQLException;
  void deleteAccount(UUID uuid) throws SQLException;

  enum Key {
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

  // Default implementations for setting values
  default void set(ACI aci, IAccountDataTable.Key key, int value) throws SQLException { set(aci, key, ByteBuffer.allocate(4).putInt(value).array()); }
  default void set(ACI aci, IAccountDataTable.Key key, long value) throws SQLException { set(aci, key, ByteBuffer.allocate(8).putLong(value).array()); }
  default void set(ACI aci, IAccountDataTable.Key key, String value) throws SQLException { set(aci, key, value != null ? value.getBytes() : new byte[] {}); }
  default void set(ACI aci, IAccountDataTable.Key key, boolean value) throws SQLException { set(aci, key, value ? 1 : 0); }

  // Default implementations for getting values
  default int getInt(ACI aci, IAccountDataTable.Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? ByteBuffer.wrap(bytes).getInt() : -1;
  }

  default long getLong(ACI aci, IAccountDataTable.Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? ByteBuffer.wrap(bytes).getLong() : -1;
  }

  default String getString(ACI aci, IAccountDataTable.Key key) throws SQLException {
    var bytes = getBytes(aci, key);
    return bytes != null ? new String(bytes) : null;
  }

  default Boolean getBoolean(ACI aci, IAccountDataTable.Key key) throws SQLException {
    var val = getInt(aci, key);
    return val > -1 ? (val == 1) : null;
  }
}
