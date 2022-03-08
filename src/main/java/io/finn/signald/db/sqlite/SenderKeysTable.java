/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.ISenderKeysTable;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeysTable implements ISenderKeysTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_keys";

  private final ACI aci;

  public SenderKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public void storeSenderKey(SignalProtocolAddress address, UUID distributionId, SenderKeyRecord record) {
    try {
      var query = "INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ADDRESS + "," + DEVICE + "," + DISTRIBUTION_ID + "," + RECORD + "," + CREATED_AT +
                  ") VALUES (?, ?, ?, ?, ?, ?)";
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, aci.toString());
        statement.setString(2, address.getName());
        statement.setInt(3, address.getDeviceId());
        statement.setString(4, distributionId.toString());
        statement.setBytes(5, record.serialize());
        statement.setLong(6, System.currentTimeMillis());
        Database.executeUpdate(TABLE_NAME + "_store", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress address, UUID distributionId) {
    try {
      var query = "SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, aci.toString());
        statement.setString(2, address.getName());
        statement.setInt(3, address.getDeviceId());
        statement.setString(4, distributionId.toString());
        try (var rows = Database.executeQuery(TABLE_NAME + "_load", statement)) {
          return rows.next() ? new SenderKeyRecord(rows.getBytes(RECORD)) : null;
        }
      }
    } catch (SQLException | IOException e) {
      logger.error("unexpected error while trying to load sender key", e);
      Sentry.captureException(e);
      return null;
    }
  }

  @Override
  public long getCreatedTime(SignalProtocolAddress address, UUID distributionId) throws SQLException {
    var query = "SELECT " + CREATED_AT + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, address.getName());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_created_time", statement)) {
        return rows.next() ? rows.getLong(CREATED_AT) : -1;
      }
    }
  }

  @Override
  public void deleteAllFor(String address, DistributionId distributionId) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DISTRIBUTION_ID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, address);
      statement.setString(3, distributionId.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for", statement);
    }
  }

  @Override
  public void deleteAccount(UUID uuid) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, uuid.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
