/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IPreKeysTable;
import io.sentry.Sentry;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.whispersystems.signalservice.api.push.ACI;

public class PreKeysTable implements IPreKeysTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "signald_prekeys";

  private final ACI aci;

  public PreKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, preKeyId);
        try (var rows = Database.executeQuery(TABLE_NAME + "_load_pre_key", statement)) {
          if (!rows.next()) {
            throw new InvalidKeyIdException("prekey not found");
          }
          return new PreKeyRecord(rows.getBytes(RECORD));
        }
      }
    } catch (SQLException | InvalidMessageException t) {
      throw new InvalidKeyIdException(t);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try {
      var query = String.format("INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?) ON CONFLICT (%s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                                // FIELDS
                                ACCOUNT_UUID, ID, RECORD,
                                // ON CONFLICT
                                ACCOUNT_UUID, ID,
                                // DO UPDATE SET
                                RECORD, RECORD);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, preKeyId);
        statement.setBytes(3, record.serialize());
        Database.executeUpdate(TABLE_NAME + "_store_pre_key", statement);
      }
    } catch (SQLException e) {
      logger.error("failed to store prekey", e);
      Sentry.captureException(e);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, preKeyId);
        try (var rows = Database.executeQuery(TABLE_NAME + "_contains_pre_key", statement)) {
          return rows.next();
        }
      }
    } catch (SQLException e) {
      logger.error("failed to check if prekey exists", e);
      Sentry.captureException(e);
      return false;
    }
  }

  @Override
  public void removePreKey(int preKeyId) {
    try {
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, preKeyId);
        Database.executeUpdate(TABLE_NAME + "_remove_pre_key", statement);
      }
    } catch (SQLException e) {
      logger.error("failed to delete prekey", e);
      Sentry.captureException(e);
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
