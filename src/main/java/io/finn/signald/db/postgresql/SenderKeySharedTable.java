/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

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
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeySharedTable implements ISenderKeySharedTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "signald_sender_key_shared";

  private final ACI aci;

  public SenderKeySharedTable(ACI aci) { this.aci = aci; }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    Set<SignalProtocolAddress> addresses = new HashSet<>();
    try {
      var query = String.format("SELECT %s, %s FROM %s WHERE %s=? AND %s=?", ADDRESS, DEVICE, TABLE_NAME, ACCOUNT_UUID, DISTRIBUTION_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        statement.setObject(1, aci.uuid());
        statement.setObject(2, distributionId.asUuid());

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
      var query = String.format("INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?) ON CONFLICT (%s, %s, %s) DO UPDATE SET %s=EXCLUDED.%s", TABLE_NAME,
                                // FIELDS
                                ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID,
                                // ON CONFLICT
                                ACCOUNT_UUID, ADDRESS, DEVICE,
                                // SET
                                DISTRIBUTION_ID, DISTRIBUTION_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setObject(1, aci.uuid());
          statement.setString(2, address.getName());
          statement.setInt(3, address.getDeviceId());
          statement.setObject(4, distributionId.asUuid());
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
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=? AND %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, ADDRESS, DEVICE, DISTRIBUTION_ID);
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setObject(1, aci.uuid());
          statement.setString(2, address.getName());
          statement.setInt(3, address.getDeviceId());
          statement.setObject(4, distributionId.asUuid());
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
      var query = String.format("DELETE FROM %s WHERE %s=? AND %s=? AND %s=?", TABLE_NAME, ACCOUNT_UUID, ADDRESS, DEVICE);
      try (var statement = Database.getConn().prepareStatement(query)) {
        for (SignalProtocolAddress address : addresses) {
          statement.setObject(1, aci.uuid());
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
    var query = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?", TABLE_NAME, ACCOUNT_UUID, DISTRIBUTION_ID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setObject(2, distributionId.asUuid());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for_distributionid", statement);
    }
  }

  @Override
  public void deleteForAll(Recipient recipient) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?", TABLE_NAME, ACCOUNT_UUID, ADDRESS);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setObject(2, recipient.getACI().toString());
      Database.executeUpdate(TABLE_NAME + "_delete_all_for_recipient", statement);
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

  @Override
  public void deleteSharedWith(Recipient source) throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s = ? AND %s = ?", TABLE_NAME, ACCOUNT_UUID, ADDRESS);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, aci.uuid());
      statement.setString(2, source.getACI().toString());
      Database.executeUpdate(TABLE_NAME + "_delete_shared_with", statement);
    }
  }
}
