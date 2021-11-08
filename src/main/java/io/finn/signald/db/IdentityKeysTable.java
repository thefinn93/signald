/*
 * Copyright (C) 2021 Finn Herzfeld
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

package io.finn.signald.db;

import io.finn.signald.Account;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class IdentityKeysTable implements IdentityKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "identity_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String IDENTITY_KEY = "identity_key";
  private static final String TRUST_LEVEL = "trust_level";
  private static final String ADDED = "added";

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
        b = PendingAccountDataTable.getBytes(pendingAccountIdentifier, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
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
        return PendingAccountDataTable.getInt(pendingAccountIdentifier, PendingAccountDataTable.Key.LOCAL_REGISTRATION_ID);
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
    }
    return false;
  }

  public boolean saveIdentity(String address, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, SQLException {
    Recipient recipient = account.getRecipients().get(address);
    return saveIdentity(recipient, identityKey, trustLevel);
  }
  public boolean saveIdentity(Recipient recipient, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(recipient, identityKey, trustLevel, new Date()); }
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
      PreparedStatement statement = Database.getConn().prepareStatement(query);
      statement.setString(1, account.getUUID().toString());
      statement.setInt(2, recipient.getId());
      statement.setBytes(3, identityKey.serialize());
      statement.setString(4, trustLevel.name());
      statement.setLong(5, added.getTime());
      return statement.executeUpdate() > 0;
    } catch (SQLException e) {
      logger.catching(e);
    }

    return false;
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    try {
      int recipientID = account.getRecipients().get(address.getName()).getId();
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + IDENTITY_KEY + "," + TRUST_LEVEL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
      statement.setString(1, account.getUUID().toString());
      statement.setInt(2, recipientID);
      ResultSet rows = statement.executeQuery();

      boolean moreRows = rows.next();
      if (!moreRows) {
        // no known keys, trust key on first use
        rows.close();
        return true;
      }
      while (moreRows) {
        try {
          if (identityKey.equals(new IdentityKey(rows.getBytes(IDENTITY_KEY), 0))) {
            TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
            return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
          }
        } catch (InvalidKeyException e) {
          logger.warn("Error parsing IdentityKey on row " + rows.getRow() + ": " + e.getMessage());
        }
        moreRows = rows.next();
      }
      rows.close();
      saveIdentity(address.getName(), identityKey, TrustLevel.UNTRUSTED);
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
    return false;
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    try {
      int recipientID = account.getRecipients().get(address.getName()).getId();
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + IDENTITY_KEY + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT +
                                                                        " = ? ORDER BY " + ADDED + " DESC LIMIT 1");
      statement.setString(1, account.getUUID().toString());
      statement.setInt(2, recipientID);
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        return null;
      }
      IdentityKey result = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
      rows.close();
      return result;
    } catch (SQLException | InvalidKeyException | IOException e) {
      logger.catching(e);
      return null;
    }
  }

  public List<IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException {
    PreparedStatement statement = Database.getConn().prepareStatement(
        "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + RecipientsTable.TABLE_NAME + "." + RecipientsTable.E164 + "," + IDENTITY_KEY + "," +
        TRUST_LEVEL + "," + ADDED + " FROM " + TABLE_NAME + " JOIN " + RecipientsTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT + " = " + RecipientsTable.TABLE_NAME +
        "." + RecipientsTable.ROW_ID + " WHERE " + TABLE_NAME + "." + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
    statement.setString(1, account.getUUID().toString());
    statement.setInt(2, recipient.getId());
    ResultSet row = statement.executeQuery();
    List<IdentityKeyRow> results = new ArrayList<>();
    while (row.next()) {
      String uuidstr = row.getString(RecipientsTable.UUID);
      ACI aci = null;
      if (uuidstr != null) {
        aci = ACI.from(UUID.fromString(uuidstr));
      }
      SignalServiceAddress address = new SignalServiceAddress(aci, row.getString(RecipientsTable.E164));
      IdentityKey identityKey = new IdentityKey(row.getBytes(IDENTITY_KEY), 0);
      TrustLevel trustLevel = TrustLevel.valueOf(row.getString(TRUST_LEVEL));
      Date added = new Date(row.getLong(ADDED));
      results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
    }
    row.close();
    return results;
  }

  public List<IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException {
    PreparedStatement statement = Database.getConn().prepareStatement(
        "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + RecipientsTable.TABLE_NAME + "." + RecipientsTable.E164 + "," + IDENTITY_KEY + "," +
        TRUST_LEVEL + "," + ADDED + " FROM " + TABLE_NAME + " JOIN " + RecipientsTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT + " = " + RecipientsTable.TABLE_NAME +
        "." + RecipientsTable.ROW_ID + " WHERE " + TABLE_NAME + "." + ACCOUNT_UUID + " = ?");
    statement.setString(1, account.getUUID().toString());
    ResultSet row = statement.executeQuery();
    List<IdentityKeyRow> results = new ArrayList<>();
    while (row.next()) {
      String uuidstr = row.getString(RecipientsTable.UUID);
      if (uuidstr == null) {
        continue; // no UUID no
      }
      ACI aci = ACI.from(UUID.fromString(uuidstr));
      SignalServiceAddress address = new SignalServiceAddress(aci, row.getString(RecipientsTable.E164));
      IdentityKey identityKey = new IdentityKey(row.getBytes(IDENTITY_KEY), 0);
      TrustLevel trustLevel = TrustLevel.valueOf(row.getString(TRUST_LEVEL));
      Date added = new Date(row.getLong(ADDED));
      results.add(new IdentityKeyRow(address, identityKey, trustLevel, added));
    }
    row.close();
    return results;
  }

  public static class IdentityKeyRow {
    SignalServiceAddress address;
    IdentityKey identityKey;
    TrustLevel trustLevel;
    Date added;

    public IdentityKeyRow(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
      this.address = address;
      this.identityKey = identityKey;
      this.trustLevel = trustLevel;
      this.added = added;
    }

    boolean isTrusted() { return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED; }

    public IdentityKey getKey() { return this.identityKey; }

    public TrustLevel getTrustLevel() { return this.trustLevel; }

    public Date getDateAdded() { return this.added; }

    public byte[] getFingerprint() { return identityKey.getPublicKey().serialize(); }

    public SignalServiceAddress getAddress() { return address; }

    public String getTrustLevelString() {
      if (trustLevel == null) {
        return trustLevel.TRUSTED_UNVERIFIED.name();
      }
      return trustLevel.name();
    }

    public long getAddedTimestamp() {
      if (added == null) {
        return 0;
      }
      return added.getTime();
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
