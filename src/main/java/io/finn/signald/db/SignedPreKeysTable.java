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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SignedPreKeysTable implements SignedPreKeyStore {
  private final static Logger logger = LogManager.getLogger();

  private final static String TABLE_NAME = "signed_prekeys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String ID = "id";
  private static final String RECORD = "record";

  private final UUID uuid;

  public SignedPreKeysTable(UUID u) { uuid = u; }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, signedPreKeyId);
      ResultSet rows = statement.executeQuery();
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
      statement.setString(1, uuid.toString());
      ResultSet rows = statement.executeQuery();
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
          Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ID + "," + RECORD + ") VALUES (?, ?, ?) ON CONFLICT(" + ACCOUNT_UUID + "," +
                                              ID + ") DO UPDATE SET " + RECORD + " = excluded." + RECORD);
      statement.setString(1, uuid.toString());
      statement.setInt(2, signedPreKeyId);
      statement.setBytes(3, record.serialize());
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, signedPreKeyId);
      ResultSet rows = statement.executeQuery();
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
      statement.setString(1, uuid.toString());
      statement.setInt(2, signedPreKeyId);
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.catching(e);
      throw new AssertionError(e);
    }
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
