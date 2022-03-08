/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.ISenderKeysTable;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeysTable implements ISenderKeysTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "signald_sender_keys";

  private final ACI aci;

  public SenderKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public void storeSenderKey(SignalProtocolAddress address, UUID distributionId, SenderKeyRecord record) {
    try {
      var query =
          String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (%s, %s, %s, %s) DO UPDATE SET %s=EXCLUDED.%s, %s=EXCLUDED.%s", TABLE_NAME,
                        // FIELDS
                        ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID, RECORD, CREATED_AT,
                        // ON CONFLICT
                        ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID,
                        // DO UPDATE SET
                        RECORD, RECORD, CREATED_AT, CREATED_AT);
      // account_uuid,address,device,distribution_id
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setString(2, address.getName());
        statement.setInt(3, address.getDeviceId());
        statement.setObject(4, distributionId);
        statement.setBytes(5, record.serialize());
        statement.setTimestamp(6, new Timestamp(new Date().getTime()));
        Database.executeUpdate(TABLE_NAME + "_store", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress address, UUID distributionId) {
    try {
      var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=? AND %s=?", RECORD, TABLE_NAME,
                                // WHERE
                                ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setString(2, address.getName());
        statement.setInt(3, address.getDeviceId());
        statement.setObject(4, distributionId);
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
    var query = String.format("SELECT %s FROM %s WHERE %s=? AND %s=? AND %s=? AND %s=?", CREATED_AT, TABLE_NAME,
                              // WHERE
                              ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setString(2, address.getName());
      statement.setInt(3, address.getDeviceId());
      statement.setObject(4, distributionId);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_created_time", statement)) {
        return rows.next() ? rows.getTimestamp(CREATED_AT).getTime() : -1;
      }
    }
  }

  @Override
  public void deleteAllFor(String address, DistributionId distributionId) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=? AND %s=? AND %s=?", TABLE_NAME,
                              // WHERE
                              ACCOUNT_UUID, ADDRESS, DISTRIBUTION_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setString(2, address);
      statement.setObject(3, distributionId.asUuid());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for", statement);
    }
  }

  @Override
  public void deleteAccount(UUID uuid) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s = ?", TABLE_NAME, ACCOUNT_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, uuid);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
