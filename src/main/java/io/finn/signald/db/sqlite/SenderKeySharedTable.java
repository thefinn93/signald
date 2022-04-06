/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.ISenderKeySharedTable;
import io.finn.signald.db.Recipient;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeySharedTable implements ISenderKeySharedTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_key_shared";

  private final ACI aci;

  public SenderKeySharedTable(ACI aci) { this.aci = aci; }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    Set<SignalProtocolAddress> addresses = new HashSet<>();
    try {
      var query = "SELECT " + ADDRESS + "," + DEVICE + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DISTRIBUTION_ID + " = ?";
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setString(1, aci.toString());
        statement.setString(2, distributionId.toString());

        try (var rows = Database.executeQuery(TABLE_NAME + "_get_sender_key_shared_with", statement)) {
          while (rows.next()) {
            SignalProtocolAddress address = new SignalProtocolAddress(rows.getString(ADDRESS), rows.getInt(DEVICE));
            addresses.add(address);
          }
        }
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
    return addresses;
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try {
      var query = "INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ADDRESS + "," + DEVICE + "," + DISTRIBUTION_ID + ") VALUES (?, ?, ?, ?)";
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setString(1, aci.toString());
          statement.setString(2, address.getName());
          statement.setInt(3, address.getDeviceId());
          statement.setString(4, distributionId.toString());
          statement.addBatch();
        }
        Database.executeBatch(TABLE_NAME + "_mark_sender_key_shared_with", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public void clearSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try {
      var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?";
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setString(1, aci.toString());
          statement.setString(2, address.getName());
          statement.setInt(3, address.getDeviceId());
          statement.setString(4, distributionId.toString());
          statement.addBatch();
        }
        Database.executeBatch(TABLE_NAME + "_clear_sender_key_shared_with", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses) {
    try {
      var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE + " = ?";
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setString(1, aci.toString());
          statement.setString(2, address.getName());
          statement.setInt(3, address.getDeviceId());
          statement.addBatch();
        }
        Database.executeBatch(TABLE_NAME + "_clear_sender_key_shared_with", statement);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public boolean isMultiDevice() {
    return new Account(aci).getMultiDevice();
  }

  @Override
  public void deleteAllFor(DistributionId distributionId) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DISTRIBUTION_ID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, distributionId.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for_distributionid", statement);
    }
  }

  @Override
  public void deleteForAll(Recipient recipient) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, recipient.getServiceId().toString());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for_recipient", statement);
    }
  }

  @Override
  public void deleteAccount(ACI aci) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }

  @Override
  public void deleteSharedWith(Recipient source) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setString(2, source.getServiceId().toString());
      Database.executeUpdate(TABLE_NAME + "_delete_shared_with", statement);
    }
  }
}
