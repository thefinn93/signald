/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.db.IPreKeysTable;
import io.finn.signald.db.ISessionsTable;
import io.finn.signald.db.ISignedPreKeysTable;
import java.io.IOException;
import java.sql.SQLException;
@Deprecated
public class LegacySignalProtocolStore {
  @JsonIgnore public IPreKeysTable preKeys;
  @JsonIgnore public ISessionsTable sessionStore;
  @JsonIgnore public ISignedPreKeysTable signedPreKeyStore;
  @JsonIgnore public IIdentityKeysTable identityKeyStore;

  @JsonProperty("preKeys") public LegacyPreKeyStore legacyPreKeys;
  @JsonProperty("sessionStore") public LegacySessionStore legacySessionStore;
  @JsonProperty("signedPreKeyStore") public LegacySignedPreKeyStore legacySignedPreKeyStore;
  @JsonProperty("identityKeyStore") public LegacyIdentityKeyStore legacyIdentityKeyStore;

  public LegacySignalProtocolStore() {}

  public void migrateToDB(Account account) throws SQLException, IOException {
    legacyPreKeys.migrateToDB(account);
    legacySessionStore.migrateToDB(account);
    legacySignedPreKeyStore.migrateToDB(account);
    legacyIdentityKeyStore.migrateToDB(account);
  }
}
