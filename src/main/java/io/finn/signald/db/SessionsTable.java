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

import io.finn.signald.clientprotocol.v1.JsonAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SessionsTable implements SessionStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sessions";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String DEVICE_ID = "device_id";
  private static final String RECORD = "record";

  private final UUID uuid;
  private final RecipientsTable recipientsTable;

  public SessionsTable(UUID uuid) {
    this.uuid = uuid;
    recipientsTable = new RecipientsTable(uuid);
  }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress address) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
      statement.setInt(3, address.getDeviceId());
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        logger.debug("loadSession() called but no sessions found: " + new JsonAddress(recipient.second()).toRedactedString() + " device " + address.getDeviceId());
        return new SessionRecord();
      }
      SessionRecord sessionRecord = new SessionRecord(rows.getBytes(RECORD));
      rows.close();
      return sessionRecord;
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
    return new SessionRecord();
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> list) throws NoSessionException {
    List<SessionRecord> sessions = new ArrayList<>();
    for (SignalProtocolAddress address : list) {
      try {
        Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
        PreparedStatement statement =
            Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
        statement.setString(1, uuid.toString());
        statement.setInt(2, recipient.first());
        statement.setInt(3, address.getDeviceId());
        ResultSet rows = statement.executeQuery();
        if (!rows.next()) {
          rows.close();
          throw new NoSessionException("Unable to find session for at least one recipient");
        }
        sessions.add(new SessionRecord(rows.getBytes(RECORD)));
        rows.close();
      } catch (SQLException | IOException e) {
        logger.warn("exception while loading session", e);
      }
    }
    return sessions;
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(name);
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + DEVICE_ID + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
      ResultSet rows = statement.executeQuery();
      List<Integer> results = new ArrayList<>();
      while (rows.next()) {
        int deviceId = rows.getInt(DEVICE_ID);
        if (deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID) {
          results.add(deviceId);
        }
      }
      rows.close();
      return results;
    } catch (SQLException e) {
      logger.catching(e);
      return null;
    }
  }

  @Override
  public void storeSession(SignalProtocolAddress address, SessionRecord record) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + DEVICE_ID + "," +
                                                                        RECORD + ") VALUES (?, ?, ?, ?)");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
      statement.setInt(3, address.getDeviceId());
      statement.setBytes(4, record.serialize());
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
      statement.setInt(3, address.getDeviceId());
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        return false;
      }
      SessionRecord sessionRecord = new SessionRecord(rows.getBytes(RECORD));
      rows.close();
      return sessionRecord.hasSenderChain() && sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
    } catch (SQLException | IOException e) {
      logger.catching(e);
      return false;
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
      statement.setInt(3, address.getDeviceId());
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(name);
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
      statement.setString(1, uuid.toString());
      statement.setInt(2, recipient.first());
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  public void deleteAllSessions(SignalServiceAddress address) { deleteAllSessions(address.getIdentifier()); }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }
}
