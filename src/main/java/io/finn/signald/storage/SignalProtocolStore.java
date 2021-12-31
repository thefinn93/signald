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
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.PreKeysTable;
import io.finn.signald.db.SessionsTable;
import io.finn.signald.db.SignedPreKeysTable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

public class SignalProtocolStore {
  @JsonIgnore public PreKeysTable preKeys;
  @JsonIgnore public SessionsTable sessionStore;
  @JsonIgnore public SignedPreKeysTable signedPreKeyStore;
  @JsonIgnore public IdentityKeysTable identityKeyStore;

  @JsonProperty("preKeys") public PreKeyStore legacyPreKeys;
  @JsonProperty("sessionStore") public SessionStore legacySessionStore;
  @JsonProperty("signedPreKeyStore") public SignedPreKeyStore legacySignedPreKeyStore;
  @JsonProperty("identityKeyStore") public IdentityKeyStore legacyIdentityKeyStore;

  public SignalProtocolStore() {}

  public void migrateToDB(Account account) throws SQLException, IOException {
    legacyPreKeys.migrateToDB(account);
    legacySessionStore.migrateToDB(account);
    legacySignedPreKeyStore.migrateToDB(account);
    legacyIdentityKeyStore.migrateToDB(account);
  }
}
