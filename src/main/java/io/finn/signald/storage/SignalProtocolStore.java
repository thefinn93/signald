/*
 * Copyright (C) 2020 Finn Herzfeld
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

  public void setUUID(UUID u) {
    if (preKeys == null) {
      preKeys = new PreKeysTable(u);
    }
    if (sessionStore == null) {
      sessionStore = new SessionsTable(u);
    }
    if (signedPreKeyStore == null) {
      signedPreKeyStore = new SignedPreKeysTable(u);
    }
    if (identityKeyStore == null) {
      identityKeyStore = new IdentityKeysTable(u);
    }
  }

  public void migrateToDB(Account account) throws SQLException, IOException {
    legacyPreKeys.migrateToDB(account);
    legacySessionStore.migrateToDB(account);
    legacySignedPreKeyStore.migrateToDB(account);
    legacyIdentityKeyStore.migrateToDB(account);
  }
}
