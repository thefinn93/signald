/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.Account;
import io.finn.signald.Config;
import io.finn.signald.db.Database;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class IdentityKeysTable implements IIdentityKeysTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "identity_keys";

  private Account account;
  private String pendingAccountIdentifier;

  public IdentityKeysTable(ACI aci) { account = new Account(aci); }

  public IdentityKeysTable(String pendingAccountIdentifier) { this.pendingAccountIdentifier = pendingAccountIdentifier; }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    try {
      byte[] b;
      if (pendingAccountIdentifier == null) {
        return account.getIdentityKeyPair();
      } else {
        b = Database.Get().PendingAccountDataTable.getBytes(pendingAccountIdentifier, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
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
  public boolean saveIdentity(Recipient recipient, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
    if (identityKey == null) {
      return false;
    }
    try {
      String query;
      if (trustLevel != null) {
        query = "INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + IDENTITY_KEY + "," + TRUST_LEVEL + "," + ADDED +
                ") VALUES (?, ?, ?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," + RECIPIENT + "," + IDENTITY_KEY + ") DO UPDATE SET " + TRUST_LEVEL + " = excluded." + TRUST_LEVEL;
      } else {
        query = "INSERT OR IGNORE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + IDENTITY_KEY + "," + TRUST_LEVEL + "," + ADDED + ") VALUES (?, ?, ?, ?, ?)";
        trustLevel = TrustLevel.TRUSTED_UNVERIFIED;
      }
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, account.getUUID().toString());
        statement.setInt(2, recipient.getId());
        statement.setBytes(3, identityKey.serialize());
        statement.setString(4, trustLevel.name());
        statement.setLong(5, added.getTime());
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
      var query = "SELECT " + IDENTITY_KEY + "," + TRUST_LEVEL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?";
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, account.getUUID().toString());
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
      var query = "SELECT " + IDENTITY_KEY + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? ORDER BY " + ADDED + " DESC LIMIT 1";
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, account.getUUID().toString());
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
  public List<IIdentityKeysTable.IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException {
    var query = "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + RecipientsTable.TABLE_NAME + "." + RecipientsTable.E164 + "," + IDENTITY_KEY + "," +
                TRUST_LEVEL + "," + ADDED + " FROM " + TABLE_NAME + " JOIN " + RecipientsTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT + " = " +
                RecipientsTable.TABLE_NAME + "." + RecipientsTable.ROW_ID + " WHERE " + TABLE_NAME + "." + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account.getUUID().toString());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_identities", statement)) {
        var results = new ArrayList<IIdentityKeysTable.IdentityKeyRow>();
        while (rows.next()) {
          String uuidstr = rows.getString(RecipientsTable.UUID);
          ACI aci = null;
          if (uuidstr != null) {
            aci = ACI.from(UUID.fromString(uuidstr));
          }
          SignalServiceAddress address = new SignalServiceAddress(aci, rows.getString(RecipientsTable.E164));
          IdentityKey identityKey = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
          TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
          Date added = new Date(rows.getLong(ADDED));
          results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
        }
        return results;
      }
    }
  }

  @Override
  public List<IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException {
    var query = "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + RecipientsTable.TABLE_NAME + "." + RecipientsTable.E164 + "," + IDENTITY_KEY + "," +
                TRUST_LEVEL + "," + ADDED + " FROM " + TABLE_NAME + " JOIN " + RecipientsTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT + " = " +
                RecipientsTable.TABLE_NAME + "." + RecipientsTable.ROW_ID + " WHERE " + TABLE_NAME + "." + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account.getUUID().toString());
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
          Date added = new Date(rows.getLong(ADDED));
          results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
        }
        return results;
      }
    }
  }

  @Override
  public void deleteAccount(UUID uuid) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, uuid.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  @Override
  public void trustAllKeys() throws SQLException {
    logger.info("marking all currently UNTRUSTED keys in as TRUSTED_UNVERIFIED for all accounts");
    var query = "UPDATE " + TABLE_NAME + " SET " + TRUST_LEVEL + " = ? WHERE " + TRUST_LEVEL + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, TrustLevel.TRUSTED_UNVERIFIED.name());
      statement.setString(2, TrustLevel.UNTRUSTED.name());
      var count = Database.executeUpdate(TABLE_NAME + "_trust_all_existing_keys", statement);
      logger.info("marked {} key(s) as TRUSTED_UNVERIFIED", count);
    }
  }
}
