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

import io.finn.signald.exceptions.InvalidAddressException;
import io.finn.signald.util.AddressUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class IdentityKeysTable implements IdentityKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "identity_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String IDENTITY_KEY = "identity_key";
  private static final String TRUST_LEVEL = "trust_level";
  private static final String ADDED = "added";

  private UUID uuid;
  private String pendingAccountIdentifier;

  public IdentityKeysTable(UUID u) { uuid = u; }

  public IdentityKeysTable(String pendingAccountIdentifier) { this.pendingAccountIdentifier = pendingAccountIdentifier; }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    try {
      byte[] b;
      if (pendingAccountIdentifier == null) {
        b = AccountDataTable.getBytes(uuid, AccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
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
        return AccountDataTable.getInt(uuid, AccountDataTable.Key.LOCAL_REGISTRATION_ID);
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
    return saveIdentity(address.getName(), identityKey, TrustLevel.TRUSTED_UNVERIFIED);
  }

  public boolean saveIdentity(String address, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(AddressUtil.fromIdentifier(address), identityKey, trustLevel); }
  public boolean saveIdentity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(address, identityKey, trustLevel, new Date()); }
  public boolean saveIdentity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
    try {
      Integer recipientID = new RecipientsTable(uuid).get(address).first();
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + IDENTITY_KEY + "," +
                                                                        TRUST_LEVEL + "," + ADDED + ") VALUES (?, ?, ?, ?, ?)");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipientID);
      statement.setBytes(3, identityKey.serialize());
      statement.setString(4, trustLevel.name());
      statement.setLong(5, added.getTime());
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.catching(e);
    }

    return false;
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    try {
      Integer recipientID = new RecipientsTable(uuid).get(address.getName()).first();
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + IDENTITY_KEY + "," + TRUST_LEVEL + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID +
                                                                        " = ? AND " + RECIPIENT + " = ? AND " + IDENTITY_KEY + " != ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipientID);
      statement.setBytes(3, identityKey.serialize());
      ResultSet rows = statement.executeQuery();

      boolean moreRows = rows.next();
      if (!moreRows) {
        // no known keys, trust key on first use
        rows.close();
        return true;
      }
      while (moreRows) {
        try {
          IdentityKey key = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
          if (key.equals(identityKey)) {
            TrustLevel trustLevel = TrustLevel.valueOf(rows.getString(TRUST_LEVEL));
            return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED;
          }
        } catch (InvalidKeyException e) {
          logger.warn("Error parsing IdentityKey on row " + rows.getRow() + ": " + e.getMessage());
        }
        moreRows = rows.next();
      }
      rows.close();
    } catch (SQLException e) {
      logger.catching(e);
    }
    return false;
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    try {
      Integer recipientID = new RecipientsTable(uuid).get(address.getName()).first();
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + IDENTITY_KEY + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT +
                                                                        " = ? ORDER BY " + ADDED + " LIMIT 1");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipientID);
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        return null;
      }
      IdentityKey result = new IdentityKey(rows.getBytes(IDENTITY_KEY), 0);
      rows.close();
      return result;
    } catch (SQLException | InvalidKeyException e) {
      logger.catching(e);
      return null;
    }
  }

  public List<IdentityKeyRow> getIdentities(SignalServiceAddress a) throws SQLException, InvalidKeyException, InvalidAddressException {
    Integer recipient = new RecipientsTable(uuid).get(a).first();
    PreparedStatement statement = Database.getConn().prepareStatement(
        "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + RecipientsTable.TABLE_NAME + "." + RecipientsTable.E164 + "," + IDENTITY_KEY + "," +
        TRUST_LEVEL + "," + ADDED + " FROM " + TABLE_NAME + " JOIN " + RecipientsTable.TABLE_NAME + " ON " + TABLE_NAME + "." + RECIPIENT + " = " + RecipientsTable.TABLE_NAME +
        "." + RecipientsTable.ROW_ID + " WHERE " + TABLE_NAME + "." + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
    statement.setString(1, uuid.toString());
    statement.setInt(2, recipient);
    ResultSet row = statement.executeQuery();
    List<IdentityKeyRow> results = new ArrayList<>();
    while (row.next()) {
      String uuidstr = row.getString(RecipientsTable.UUID);
      UUID uuid = null;
      if (uuidstr != null) {
        uuid = UUID.fromString(uuidstr);
      }
      SignalServiceAddress address = new SignalServiceAddress(uuid, row.getString(RecipientsTable.E164));
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
    statement.setString(1, uuid.toString());
    ResultSet row = statement.executeQuery();
    List<IdentityKeyRow> results = new ArrayList<>();
    while (row.next()) {
      String uuidstr = row.getString(RecipientsTable.UUID);
      UUID uuid = null;
      if (uuidstr != null) {
        uuid = UUID.fromString(uuidstr);
      }
      SignalServiceAddress address = new SignalServiceAddress(uuid, row.getString(RecipientsTable.E164));
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
