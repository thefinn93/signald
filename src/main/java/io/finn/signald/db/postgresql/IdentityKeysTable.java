/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.Account;
import io.finn.signald.Config;
import io.finn.signald.db.Database;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class IdentityKeysTable implements IIdentityKeysTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "signald_identity_keys";

  private Account account;
  private String pendingAccountIdentifier;

  public IdentityKeysTable(ACI aci) { account = new Account(aci); }

  public IdentityKeysTable(String pendingAccountIdentifier) { this.pendingAccountIdentifier = pendingAccountIdentifier; }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    try {
      byte[] b;
      if (pendingAccountIdentifier == null) {
        return account.getACIIdentityKeyPair();
      } else {
        b = Database.Get().PendingAccountDataTable.getBytes(pendingAccountIdentifier, PendingAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR);
      }
      if (b == null) {
        return null;
      }
      return new IdentityKeyPair(b);
    } catch (SQLException e) {
      logger.catching(e);
      return null;
    }
  }

  @Override
  public int getLocalRegistrationId() {
    try {
      if (pendingAccountIdentifier == null) {
        return account.getLocalRegistrationId();
      } else {
        return Database.Get().PendingAccountDataTable.getInt(pendingAccountIdentifier, PendingAccountDataTable.Key.LOCAL_REGISTRATION_ID);
      }
    } catch (SQLException e) {
      logger.catching(e);
      return -1;
    }
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    try {
      return saveIdentity(address.getName(), identityKey, null);
    } catch (IOException | SQLException e) {
      logger.error("error saving new identity to identity keys table", e);
      Sentry.captureException(e);
    }
    return false;
  }

  @Override
  public boolean saveIdentity(String address, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, SQLException {
    return saveIdentity(Database.Get(account.getACI()).RecipientsTable.get(address), identityKey, trustLevel);
  }

  @Override
  public boolean saveIdentity(Recipient recipient, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
    if (identityKey == null) {
      return false;
    }
    try {
      String query;
      if (trustLevel != null) {
        query = String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?) ON CONFLICT (%s, %s, %s) DO UPDATE SET %s=EXCLUDED.%s",
                              // INSERT INTO
                              TABLE_NAME,
                              // COLUMNS
                              ACCOUNT_UUID, RECIPIENT, IDENTITY_KEY, TRUST_LEVEL, ADDED,
                              // ON CONFLICT
                              ACCOUNT_UUID, RECIPIENT, IDENTITY_KEY,
                              // DO UPDATE SET
                              TRUST_LEVEL, TRUST_LEVEL);
      } else {
        query = String.format("INSERT INTO %s (%s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?) ON CONFLICT (%s, %s, %s) DO NOTHING",
                              // INSERT INTO
                              TABLE_NAME,
                              // COLUMNS
                              ACCOUNT_UUID, RECIPIENT, IDENTITY_KEY, TRUST_LEVEL, ADDED,
                              // ON CONFLICT
                              ACCOUNT_UUID, RECIPIENT, IDENTITY_KEY);
        trustLevel = TrustLevel.TRUSTED_UNVERIFIED;
      }
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, account.getUUID());
        statement.setInt(2, recipient.getId());
        statement.setBytes(3, identityKey.serialize());
        statement.setString(4, trustLevel.name());
        statement.setTimestamp(5, new Timestamp(added.getTime()));
        return Database.executeUpdate(TABLE_NAME + "_save_identity", statement) > 0;
      }
    } catch (SQLException e) {
      logger.catching(e);
    }

    return false;
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    try {
      Recipient recipient = Database.Get(account.getACI()).RecipientsTable.get(address.getName());
      var query = String.format("SELECT %s, %s FROM %s WHERE %s=? AND %s=?", IDENTITY_KEY, TRUST_LEVEL, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, account.getUUID());
        statement.setInt(2, recipient.getId());
        try (var rows = Database.executeQuery(TABLE_NAME + "_is_trusted_identity", statement)) {
          boolean moreRows = rows.next();
          if (!moreRows) {
            // no known keys, trust key on first use
            return true;
          }
          while (moreRows) {
            try {
              if (identityKey.equals(new IdentityKey(rows.getBytes(IDENTITY_KEY), 0))) {
                TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
                return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
              }
            } catch (InvalidKeyException e) {
              logger.warn("Error parsing IdentityKey on row {}: {}", rows.getRow(), e.getMessage());
            }
            moreRows = rows.next();
          }
        }
      }
      saveIdentity(address.getName(), identityKey, Config.getNewKeyTrustLevel());

      // no existing key found, archive sessions and re-share sender keys
      account.getProtocolStore().archiveSession(address);
      Database.Get(account.getACI()).SenderKeySharedTable.deleteForAll(recipient);
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }

    if (Config.getNewKeyTrustLevel() == TrustLevel.TRUSTED_UNVERIFIED) {
      logger.debug("new identity key found for remote user, marking trusted anyway");
      return true;
    } else {
      return false;
    }
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    try {
      int recipientID = Database.Get(account.getACI()).RecipientsTable.get(address.getName()).getId();
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? ORDER BY %s DESC LIMIT 1", IDENTITY_KEY, TABLE_NAME, ACCOUNT_UUID, RECIPIENT, ADDED);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, account.getUUID());
        statement.setInt(2, recipientID);
        try (var rows = Database.executeQuery(TABLE_NAME + "_get_identity", statement)) {
          return rows.next() ? new IdentityKey(rows.getBytes(IDENTITY_KEY), 0) : null;
        }
      }
    } catch (SQLException | InvalidKeyException | IOException e) {
      logger.catching(e);
      return null;
    }
  }

  @Override
  public List<IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException {
    var query = String.format("SELECT %s.%s, %s.%s, %s, %s, %s FROM %s JOIN %s ON %s.%s=%s.%s WHERE %s.%s=? AND %s=?",
                              // SELECT
                              RecipientsTable.TABLE_NAME, RecipientsTable.UUID, // recipients.uuid
                              RecipientsTable.TABLE_NAME, RecipientsTable.E164, // recipients.e164
                              IDENTITY_KEY, TRUST_LEVEL, ADDED,
                              // FROM
                              TABLE_NAME,
                              // JOIN
                              RecipientsTable.TABLE_NAME, TABLE_NAME, RECIPIENT, RecipientsTable.TABLE_NAME, RecipientsTable.ROW_ID,
                              // WHERE
                              TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_identities", statement)) {
        List<IdentityKeyRow> results = new ArrayList<>();
        while (rows.next()) {
          String uuidstr = rows.getString(RecipientsTable.UUID);
          ACI aci = null;
          if (uuidstr != null) {
            aci = ACI.from(UUID.fromString(uuidstr));
          }
          SignalServiceAddress address = new SignalServiceAddress(aci, rows.getString(RecipientsTable.E164));
          IdentityKey identityKey = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
          TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
          Date added = rows.getTimestamp(ADDED);
          results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
        }
        return results;
      }
    }
  }

  @Override
  public List<IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException {
    var query = String.format("SELECT %s.%s, %s.%s, %s, %s, %s FROM %s JOIN %s ON %s.%s=%s.%s WHERE %s.%s=?",
                              // SELECT
                              RecipientsTable.TABLE_NAME, RecipientsTable.UUID, // recipients.uuid
                              RecipientsTable.TABLE_NAME, RecipientsTable.E164, // recipients.e164
                              IDENTITY_KEY, TRUST_LEVEL, ADDED,
                              // FROM
                              TABLE_NAME,
                              // JOIN
                              RecipientsTable.TABLE_NAME, TABLE_NAME, RECIPIENT, RecipientsTable.TABLE_NAME, RecipientsTable.ROW_ID,
                              // WHERE
                              TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      try (var rows = Database.executeQuery(TABLE_NAME + "_all_get_identities", statement)) {
        List<IdentityKeyRow> results = new ArrayList<>();
        while (rows.next()) {
          String uuidstr = rows.getString(RecipientsTable.UUID);
          if (uuidstr == null) {
            continue; // no UUID no
          }
          ACI aci = ACI.from(UUID.fromString(uuidstr));
          SignalServiceAddress address = new SignalServiceAddress(aci, rows.getString(RecipientsTable.E164));
          IdentityKey identityKey = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
          TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
          Date added = rows.getTimestamp(ADDED);
          results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
        }
        return results;
      }
    }
  }

  @Override
  public void deleteAccount(ACI aci) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  @Override
  public void trustAllKeys() throws SQLException {
    logger.info("marking all currently UNTRUSTED keys in as TRUSTED_UNVERIFIED for all accounts");
    var query = String.format("UPDATE %s SET %s=? WHERE %s=?", TABLE_NAME, TRUST_LEVEL, TRUST_LEVEL);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, TrustLevel.TRUSTED_UNVERIFIED.name());
      statement.setString(2, TrustLevel.UNTRUSTED.name());
      var count = Database.executeUpdate(TABLE_NAME + "_trust_all_existing_keys", statement);
      logger.info("marked {} key(s) as TRUSTED_UNVERIFIED", count);
    }
  }
}
