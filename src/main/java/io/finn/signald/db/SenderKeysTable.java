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
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.groups.state.SenderKeyStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeysTable implements SenderKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String ADDRESS = "address";
  private static final String DEVICE = "device";
  private static final String DISTRIBUTION_ID = "distribution_id";
  private static final String RECORD = "record";
  private static final String CREATED_AT = "created_at";

  private final ACI aci;

  public SenderKeysTable(ACI aci) { this.aci = aci; }

  @Override
  public void storeSenderKey(SignalProtocolAddress address, UUID distributionId, SenderKeyRecord record) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ADDRESS + "," + DEVICE + "," +
                                                                        DISTRIBUTION_ID + "," + RECORD + "," + CREATED_AT + ") VALUES (?, ?, ?, ?, ?, ?)");
      statement.setString(1, aci.toString());
      statement.setString(2, address.getName());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      statement.setBytes(5, record.serialize());
      statement.setLong(6, System.currentTimeMillis());
      Database.executeUpdate(TABLE_NAME + "_store", statement);
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress address, UUID distributionId) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS +
                                                                        " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setString(2, address.getName());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      ResultSet rows = Database.executeQuery(TABLE_NAME + "_load", statement);
      if (!rows.next()) {
        rows.close();
        return null;
      }
      SenderKeyRecord record = new SenderKeyRecord(rows.getBytes(RECORD));
      rows.close();
      return record;
    } catch (SQLException | IOException e) {
      logger.error("unexpected error while trying to load sender key", e);
    }
    return null;
  }

  public long getCreatedTime(SignalProtocolAddress address, UUID distributionId) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + CREATED_AT + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS +
                                                                      " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?");
    statement.setString(1, aci.toString());
    statement.setString(2, address.getName());
    statement.setInt(3, address.getDeviceId());
    statement.setString(4, distributionId.toString());
    ResultSet rows = Database.executeQuery(TABLE_NAME + "_get_created_time", statement);
    if (!rows.next()) {
      rows.close();
      return -1;
    }
    long createdTime = rows.getLong(CREATED_AT);
    rows.close();
    return createdTime;
  }

  public void deleteAllFor(String address, DistributionId distributionId) throws SQLException {
    PreparedStatement statement =
        Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DISTRIBUTION_ID + " = ?");
    statement.setString(1, aci.toString());
    statement.setString(2, address);
    statement.setString(3, distributionId.toString());
    Database.executeUpdate(TABLE_NAME + "_delete_all_for", statement);
  }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
  }
}
