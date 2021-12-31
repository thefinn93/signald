/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.signalservice.api.push.ACI;

public class PreKeysTable implements PreKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "prekeys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String ID = "id";
  private static final String RECORD = "record";

  private final ACI aci;

  public PreKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, preKeyId);
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        throw new InvalidKeyIdException("prekey not found");
      }
      PreKeyRecord record = new PreKeyRecord(rows.getBytes(RECORD));
      rows.close();
      return record;
    } catch (SQLException | IOException t) {
      throw new InvalidKeyIdException(t);
    }
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    try {
      PreparedStatement statement =
          Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ID + "," + RECORD + ") VALUES (?, ?, ?);");
      statement.setString(1, aci.toString());
      statement.setInt(2, preKeyId);
      statement.setBytes(3, record.serialize());
      statement.executeUpdate();
    } catch (SQLException t) {
      logger.error("failed to store prekey", t);
    }
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, preKeyId);
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        return false;
      }
      rows.close();
      return true;
    } catch (SQLException t) {
      logger.error("failed to check if prekey exists", t);
      return false;
    }
  }

  @Override
  public void removePreKey(int preKeyId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, preKeyId);
      statement.executeUpdate();
    } catch (SQLException t) {
      logger.error("failed to delete prekey", t);
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
