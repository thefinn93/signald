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

import io.finn.signald.util.AddressUtil;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionsTable implements SessionStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sessions";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String DEVICE_ID = "device_id";
  private static final String RECORD = "record";

  private final ACI aci;
  private final RecipientsTable recipientsTable;

  public SessionsTable(ACI aci) {
    this.aci = aci;
    recipientsTable = new RecipientsTable(aci);
  }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress address) {
    try {
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
      statement.setInt(3, address.getDeviceId());
      ResultSet rows = statement.executeQuery();
      if (!rows.next()) {
        rows.close();
        logger.debug("loadSession() called but no sessions found: " + recipient.toRedactedString() + " device " + address.getDeviceId());
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
        Recipient recipient = recipientsTable.get(address.getName());
        PreparedStatement statement =
            Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
        statement.setString(1, aci.toString());
        statement.setInt(2, recipient.getId());
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
      Recipient recipient = recipientsTable.get(name);
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + DEVICE_ID + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
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
    } catch (SQLException | IOException e) {
      logger.catching(e);
      return null;
    }
  }

  @Override
  public void storeSession(SignalProtocolAddress address, SessionRecord record) {
    try {
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + DEVICE_ID + "," +
                                                                        RECORD + ") VALUES (?, ?, ?, ?)");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
      statement.setInt(3, address.getDeviceId());
      statement.setBytes(4, record.serialize());
      statement.executeUpdate();
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    try {
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
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
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement =
          Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ? AND " + DEVICE_ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
      statement.setInt(3, address.getDeviceId());
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    try {
      Recipient recipient = recipientsTable.get(name);
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  public void deleteAllSessions(Recipient recipient) { deleteAllSessions(recipient.getAddress().getIdentifier()); }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    statement.executeUpdate();
  }

  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> list) {
    List<SignalServiceAddress> addressList = list.stream().map(AddressUtil::fromIdentifier).collect(Collectors.toList());
    try {
      List<Recipient> recipientList = recipientsTable.get(addressList);

      String query = "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + DEVICE_ID + "," + RECORD + " FROM " + TABLE_NAME + "," +
                     RecipientsTable.TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.ROW_ID + " = " + RECIPIENT + " AND (";
      for (int i = 0; i < recipientList.size() - 1; i++) {
        query += RECIPIENT + " = ? OR";
      }
      query += RECIPIENT + " = ?)";

      PreparedStatement statement = Database.getConn().prepareStatement(query);
      int i = 0;
      statement.setString(i++, aci.toString());
      for (Recipient recipient : recipientList) {
        statement.setInt(i++, recipient.getId());
      }
      ResultSet rows = statement.executeQuery();
      Set<SignalProtocolAddress> results = new HashSet<>();
      while (rows.next()) {
        String name = rows.getString(RecipientsTable.UUID);
        int deviceId = rows.getInt(DEVICE_ID);
        SessionRecord record = new SessionRecord(rows.getBytes(RECORD));
        if (record.hasSenderChain() && record.getSessionVersion() == CiphertextMessage.CURRENT_VERSION) { // signal-cli calls this "isActive"
          results.add(new SignalProtocolAddress(name, deviceId));
        }
      }
      rows.close();
      return results;
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
    return new HashSet<>();
  }
}
