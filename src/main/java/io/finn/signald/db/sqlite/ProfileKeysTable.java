package io.finn.signald.db.sqlite;

import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.IProfileKeysTable;
import io.finn.signald.db.Recipient;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.ACI;

public class ProfileKeysTable implements IProfileKeysTable {
  private static final String TABLE_NAME = "profile_keys";
  private Account account;

  public ProfileKeysTable(ACI aci) { account = new Account(aci); }

  private byte[] getBytes(Recipient recipient, String field) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? LIMIT 1", field, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_" + field, statement)) {
        if (!rows.next()) {
          return null;
        }
        return rows.getBytes(field);
      }
    }
  }

  private void set(Recipient recipient, String field, byte[] value) throws SQLException {
    var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s = excluded.%s", TABLE_NAME, ACCOUNT_UUID, RECIPIENT, field,
                              ACCOUNT_UUID, RECIPIENT, field, field);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      statement.setBytes(3, value);
      Database.executeUpdate(TABLE_NAME + "_set_" + field, statement);
    }
  }

  @Override
  public ProfileKey getProfileKey(Recipient recipient) throws SQLException {
    byte[] profileKey = getBytes(recipient, PROFILE_KEY);
    try {
      return profileKey == null ? null : new ProfileKey(profileKey);
    } catch (InvalidInputException e) {
      LogManager.getLogger().error("error parsing profile key stored in database", e);
      return null;
    }
  }

  @Override
  public void setProfileKey(Recipient recipient, ProfileKey profileKey) throws SQLException {
    set(recipient, PROFILE_KEY, profileKey == null ? null : profileKey.serialize());
  }

  @Override
  public ExpiringProfileKeyCredential getExpiringProfileKeyCredential(Recipient recipient) throws SQLException, InvalidInputException {
    byte[] profileKeyCredential = getBytes(recipient, PROFILE_KEY_CREDENTIAL);
    return profileKeyCredential == null ? null : new ExpiringProfileKeyCredential(profileKeyCredential);
  }

  @Override
  public void setExpiringProfileKeyCredential(Recipient recipient, ExpiringProfileKeyCredential profileKeyCredential) throws SQLException {
    set(recipient, PROFILE_KEY_CREDENTIAL, profileKeyCredential == null ? null : profileKeyCredential.serialize());
  }

  @Override
  public boolean isRequestPending(Recipient recipient) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? LIMIT 1", REQUEST_PENDING, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_" + REQUEST_PENDING, statement)) {
        if (!rows.next()) {
          return false;
        }
        return rows.getBoolean(REQUEST_PENDING);
      }
    }
  }

  @Override
  public void setRequestPending(Recipient recipient, boolean isRequestPending) throws SQLException {
    var query = String.format("UPDATE %s SET %s=? WHERE %s=? AND %s=?", TABLE_NAME, REQUEST_PENDING, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setBoolean(1, isRequestPending);
      statement.setObject(2, account.getUUID());
      statement.setInt(3, recipient.getId());
      Database.executeUpdate(TABLE_NAME + "_set_" + REQUEST_PENDING, statement);
    }
  }

  @Override
  public UnidentifiedAccessMode getUnidentifiedAccessMode(Recipient recipient) throws SQLException {
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? LIMIT 1", UNIDENTIFIED_ACCESS_MODE, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, account.getUUID());
      statement.setInt(2, recipient.getId());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_" + UNIDENTIFIED_ACCESS_MODE, statement)) {
        if (!rows.next()) {
          return UnidentifiedAccessMode.UNKNOWN;
        }
        return UnidentifiedAccessMode.fromMode(rows.getInt(UNIDENTIFIED_ACCESS_MODE));
      }
    }
  }

  @Override
  public void setUnidentifiedAccessMode(Recipient recipient, UnidentifiedAccessMode mode) throws SQLException {
    var query = String.format("UPDATE %s SET %s=? WHERE %s=? AND %s=?", TABLE_NAME, UNIDENTIFIED_ACCESS_MODE, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setInt(1, mode.getMode());
      statement.setObject(2, account.getUUID());
      statement.setInt(3, recipient.getId());
      Database.executeUpdate(TABLE_NAME + "_set_" + UNIDENTIFIED_ACCESS_MODE, statement);
    }
  }
}
