/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.ISignedPreKeysTable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.push.ACI;

public class SignedPreKeysTable implements ISignedPreKeysTable {
  private final static Logger logger = LogManager.getLogger();

  private final static String TABLE_NAME = "signald_signed_prekeys";

  private final ACI aci;

  public SignedPreKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, signedPreKeyId);
        try (var rows = Database.executeQuery(TABLE_NAME + "_load_signed_prekey", statement)) {
          if (!rows.next()) {
            throw new InvalidKeyIdException("No such signed prekey record " + signedPreKeyId);
          }
          return new SignedPreKeyRecord(rows.getBytes(RECORD));
        }
      }
    } catch (SQLException | InvalidMessageException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        try (var rows = Database.executeQuery(TABLE_NAME + "_load_all_signed_prekeys", statement)) {
          List<SignedPreKeyRecord> results = new ArrayList<>();
          while (rows.next()) {
            results.add(new SignedPreKeyRecord(rows.getBytes(RECORD)));
          }
          return results;
        }
      }
    } catch (SQLException | InvalidMessageException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try {
      var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s=excluded.%s", TABLE_NAME,
                                // FIELDS
                                ACCOUNT_UUID, ID, RECORD,
                                // ON CONFLICT
                                ACCOUNT_UUID, ID,
                                // DO UPDATE SET
                                RECORD, RECORD);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, signedPreKeyId);
        statement.setBytes(3, record.serialize());
        Database.executeUpdate(TABLE_NAME + "_store_signed_prekey", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, signedPreKeyId);
        try (var rows = Database.executeQuery(TABLE_NAME + "_contains_signed_prekey", statement)) {
          return rows.next();
        }
      }
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    try {
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, signedPreKeyId);
        Database.executeUpdate(TABLE_NAME + "_remove_signed_prekey", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
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
}
