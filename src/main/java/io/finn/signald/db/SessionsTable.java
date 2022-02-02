/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.util.AddressUtil;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionsTable implements SessionStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sessions";
  private static final String ROW_ID = "rowid";
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
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_load", statement);
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
        ResultSet rows = Database.executeQuery(TABLE_NAME + "_load_existing", statement);
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
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_get_sub_device_session", statement);
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
      Database.executeUpdate(TABLE_NAME + "_store", statement);
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
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_contains", statement);
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
      Database.executeUpdate(TABLE_NAME + "_delete", statement);
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
      Database.executeUpdate(TABLE_NAME + "_delete_all", statement);
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  public void deleteAllSessions(Recipient recipient) { deleteAllSessions(recipient.getAddress().getIdentifier()); }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
  }

  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> list) {
    List<SignalServiceAddress> addressList = list.stream().map(AddressUtil::fromIdentifier).collect(Collectors.toList());
    try {
      List<Recipient> recipientList = recipientsTable.get(addressList);

      String query = "SELECT " + RecipientsTable.TABLE_NAME + "." + RecipientsTable.UUID + "," + DEVICE_ID + "," + RECORD + " FROM " + TABLE_NAME + "," +
                     RecipientsTable.TABLE_NAME + " WHERE " + TABLE_NAME + '.' + ACCOUNT_UUID + " = ? AND " + RecipientsTable.TABLE_NAME + "." + ROW_ID + " = " + RECIPIENT +
                     " AND (";
      for (int i = 0; i < recipientList.size() - 1; i++) {
        query += RECIPIENT + " = ? OR ";
      }
      query += RECIPIENT + " = ?)";

      PreparedStatement statement = Database.getConn().prepareStatement(query);
      int i = 1;
      statement.setString(i++, aci.toString());
      for (Recipient recipient : recipientList) {
        statement.setInt(i++, recipient.getId());
      }
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_get_addresses_with_active_sessions", statement);
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

  public void archiveAllSessions(Recipient recipient) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("SELECT " + RECORD + "," + DEVICE_ID + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT + " = ?");
    statement.setString(1, aci.toString());
    statement.setInt(2, recipient.getId());
    ResultSet rows = Database.executeQuery(TABLE_NAME + "_archive_all_sessions_find", statement);

    List<Pair<Integer, SessionRecord>> records = new ArrayList<>();
    while (rows.next()) {
      int deviceId = rows.getInt(DEVICE_ID);
      SessionRecord record;
      try {
        record = new SessionRecord(rows.getBytes(RECORD));
      } catch (IOException e) {
        logger.warn("error loading session for {} device id {}", recipient.toRedactedString(), deviceId);
        continue;
      }
      record.archiveCurrentState();
      records.add(new Pair<>(deviceId, record));
    }
    rows.close();

    if (records.size() == 0) {
      logger.debug("no sessions to archive");
      return;
    }

    String storeStatementString = "INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + DEVICE_ID + "," + RECORD + ") VALUES "
                                  + "(?, ?, ?, ?), ".repeat(records.size() - 1) + "(?, ?, ?, ?)";
    PreparedStatement storeStatement = Database.getConn().prepareStatement(storeStatementString);
    int i = 1;
    for (Pair<Integer, SessionRecord> record : records) {
      storeStatement.setString(i++, aci.toString());
      storeStatement.setInt(i++, recipient.getId());
      storeStatement.setInt(i++, record.first());
      storeStatement.setBytes(i++, record.second().serialize());
    }
    int updated = Database.executeUpdate(TABLE_NAME + "_archive_all_sessions", storeStatement);
    logger.info("archived {} session(s) with {}", updated, recipient.toRedactedString());
  }
}
