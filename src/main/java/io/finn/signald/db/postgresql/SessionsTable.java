/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.ISessionsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.util.AddressUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SessionsTable implements ISessionsTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "signald_sessions";

  private final ACI aci;

  public SessionsTable(ACI aci) { this.aci = aci; }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress address) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(address.getName());
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, RECIPIENT, DEVICE_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        statement.setInt(3, address.getDeviceId());
        try (var rows = Database.executeQuery(TABLE_NAME + "_load", statement)) {
          if (!rows.next()) {
            logger.debug("loadSession() called but no sessions found: " + recipient.toRedactedString() + " device " + address.getDeviceId());
            return new SessionRecord();
          }
          return new SessionRecord(rows.getBytes(RECORD));
        }
      }
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
        Recipient recipient = Database.Get(aci).RecipientsTable.get(address.getName());
        var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, RECIPIENT, DEVICE_ID);
        try (var statement = Database.getConn().prepareStatement(query)) {
          statement.setObject(1, aci.uuid());
          statement.setInt(2, recipient.getId());
          statement.setInt(3, address.getDeviceId());
          try (var rows = Database.executeQuery(TABLE_NAME + "_load_existing", statement)) {
            if (!rows.next()) {
              throw new NoSessionException("Unable to find session for at least one recipient");
            }
            sessions.add(new SessionRecord(rows.getBytes(RECORD)));
          }
        }
      } catch (SQLException | IOException e) {
        logger.warn("exception while loading session", e);
      }
    }
    return sessions;
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(name);
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=?", DEVICE_ID, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        try (var rows = Database.executeQuery(TABLE_NAME + "_get_sub_device_session", statement)) {
          List<Integer> results = new ArrayList<>();
          while (rows.next()) {
            int deviceId = rows.getInt(DEVICE_ID);
            if (deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID) {
              results.add(deviceId);
            }
          }
          return results;
        }
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
      return null;
    }
  }

  @Override
  public void storeSession(SignalProtocolAddress address, SessionRecord record) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(address.getName());
      var query = String.format("INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) ON CONFLICT (%s, %s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                                // FIELDS
                                ACCOUNT_UUID, RECIPIENT, DEVICE_ID, RECORD,
                                // ON CONFLICT
                                ACCOUNT_UUID, RECIPIENT, DEVICE_ID,
                                // DO UPDATE SET
                                RECORD, RECORD);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        statement.setInt(3, address.getDeviceId());
        statement.setBytes(4, record.serialize());
        Database.executeUpdate(TABLE_NAME + "_store", statement);
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(address.getName());
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=?", RECORD, TABLE_NAME, ACCOUNT_UUID, RECIPIENT, DEVICE_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        statement.setInt(3, address.getDeviceId());
        try (var rows = Database.executeQuery(TABLE_NAME + "_contains", statement)) {
          if (!rows.next()) {
            return false;
          }
          SessionRecord sessionRecord = new SessionRecord(rows.getBytes(RECORD));
          return sessionRecord.hasSenderChain() && sessionRecord.getSessionVersion() == CiphertextMessage.CURRENT_VERSION;
        }
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
      return false;
    }
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(address.getName());
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, RECIPIENT, DEVICE_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        statement.setInt(3, address.getDeviceId());
        Database.executeUpdate(TABLE_NAME + "_delete", statement);
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  @Override
  public void deleteAllSessions(String name) {
    try {
      Recipient recipient = Database.Get(aci).RecipientsTable.get(name);
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setInt(2, recipient.getId());
        Database.executeUpdate(TABLE_NAME + "_delete_all", statement);
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  public void deleteAccount(ACI aci) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> list) {
    List<SignalServiceAddress> addressList = list.stream().map(AddressUtil::fromIdentifier).collect(Collectors.toList());
    try {
      var recipientList = Database.Get(aci).RecipientsTable.get(addressList);
      var query = String.format("SELECT %s.%s, %s, %s FROM %s, %s WHERE %s.%s=? AND %s.%s=%s AND %s IN (",
                                // FIELDS
                                RecipientsTable.TABLE_NAME, RecipientsTable.UUID, DEVICE_ID, RECORD,
                                // FROM
                                TABLE_NAME, RecipientsTable.TABLE_NAME,
                                // WHERE
                                TABLE_NAME, ACCOUNT_UUID, RecipientsTable.TABLE_NAME, ROW_ID, RECIPIENT, RECIPIENT);
      query += "?, ".repeat(recipientList.size() - 1) + " ?)";
      try (var statement = Database.getConn().prepareStatement(query)) {
        var i = 1;
        statement.setObject(i++, aci.uuid());
        for (var recipient : recipientList) {
          statement.setInt(i++, recipient.getId());
        }
        try (var rows = Database.executeQuery(TABLE_NAME + "_get_addresses_with_active_sessions", statement)) {
          Set<SignalProtocolAddress> results = new HashSet<>();
          while (rows.next()) {
            String name = rows.getString(RecipientsTable.UUID);
            int deviceId = rows.getInt(DEVICE_ID);
            SessionRecord record = new SessionRecord(rows.getBytes(RECORD));
            if (record.hasSenderChain() && record.getSessionVersion() == CiphertextMessage.CURRENT_VERSION) { // signal-cli calls this "isActive"
              results.add(new SignalProtocolAddress(name, deviceId));
            }
          }
          return results;
        }
      }
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
    return new HashSet<>();
  }

  public void archiveAllSessions(Recipient recipient) throws SQLException {
    var query = String.format("SELECT %s, %s FROM %s WHERE %s=? AND %s=?", RECORD, DEVICE_ID, TABLE_NAME, ACCOUNT_UUID, RECIPIENT);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setInt(2, recipient.getId());
      List<Pair<Integer, SessionRecord>> records = new ArrayList<>();
      try (var rows = Database.executeQuery(TABLE_NAME + "_archive_all_sessions_find", statement)) {
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
      }

      if (records.size() == 0) {
        logger.debug("no sessions to archive");
        return;
      }

      var storeStatementString = String.format("INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) ON CONFLICT (%s, %s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                                               // FIELDS
                                               ACCOUNT_UUID, RECIPIENT, DEVICE_ID, RECORD,
                                               // ON CONFLICT
                                               ACCOUNT_UUID, RECIPIENT, DEVICE_ID,
                                               // DO UPDATE SET
                                               RECORD, RECORD);
      try (var storeStatement = Database.getConn().prepareStatement(storeStatementString)) {
        for (Pair<Integer, SessionRecord> record : records) {
          storeStatement.setObject(1, aci.uuid());
          storeStatement.setInt(2, recipient.getId());
          storeStatement.setInt(3, record.first());
          storeStatement.setBytes(4, record.second().serialize());
          storeStatement.addBatch();
        }
        int updated = Database.executeBatch(TABLE_NAME + "_archive_all_sessions", storeStatement).length;
        logger.debug("archived {} session(s) with {}", updated, recipient.toRedactedString());
      }
    }
  }
}
