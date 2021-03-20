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
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.PreKeyStore;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PreKeysTable implements PreKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "prekeys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String ID = "id";
  private static final String RECORD = "record";

  private final UUID uuid;

  public PreKeysTable(UUID uuid) { this.uuid = uuid; }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ID + " = ?");
      statement.setString(1, uuid.toString());
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
      statement.setString(1, uuid.toString());
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
      statement.setString(1, uuid.toString());
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
      statement.setString(1, uuid.toString());
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
