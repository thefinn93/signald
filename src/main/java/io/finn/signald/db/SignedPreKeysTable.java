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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.signalservice.api.push.ACI;

public class SignedPreKeysTable implements SignedPreKeyStore {
  private final static Logger logger = LogManager.getLogger();

  private final static String TABLE_NAME = "signed_prekeys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String ID = "id";
  private static final String RECORD = "record";

  private final ACI aci;

  public SignedPreKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, signedPreKeyId);
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_load_signed_prekey", statement);
      if (!rows.next()) {
        rows.close();
        throw new InvalidKeyIdException("No such signed prekey record " + signedPreKeyId);
      }
      SignedPreKeyRecord result = new SignedPreKeyRecord(rows.getBytes(RECORD));
      rows.close();
      return result;
    } catch (SQLException | IOException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
      statement.setString(1, aci.toString());
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_load_all_signed_prekeys", statement);
      List<SignedPreKeyRecord> results = new ArrayList<>();
      while (rows.next()) {
        results.add(new SignedPreKeyRecord(rows.getBytes(RECORD)));
      }
      rows.close();
      return results;
    } catch (SQLException | IOException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    try {
      PreparedStatement statement =
          Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ID + "," + RECORD + ") VALUES (?, ?, ?) ON CONFLICT(" +
                                              ACCOUNT_UUID + "," + ID + ") DO UPDATE SET " + RECORD + " = excluded." + RECORD);
      statement.setString(1, aci.toString());
      statement.setInt(2, signedPreKeyId);
      statement.setBytes(3, record.serialize());
      Database.executeQuery(TABLE_NAME + "_store_signed_prekey", statement);
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, signedPreKeyId);
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_contains_signed_prekey", statement);
      boolean result = rows.next();
      rows.close();
      return result;
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, signedPreKeyId);
      Database.executeUpdate(TABLE_NAME + "_remove_signed_prekey", statement);
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
  }
}
