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

public class SenderKeysTable implements SenderKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String DEVICE = "device";
  private static final String DISTRIBUTION_ID = "distribution_id";
  private static final String RECORD = "record";

  private final ACI aci;
  private final RecipientsTable recipientsTable;

  public SenderKeysTable(ACI aci) {
    this.aci = aci;
    recipientsTable = new RecipientsTable(aci);
  }

  @Override
  public void storeSenderKey(SignalProtocolAddress address, UUID distributionId, SenderKeyRecord record) {
    try {
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + DEVICE + "," +
                                                                        DISTRIBUTION_ID + "," + RECORD + ") VALUES (?, ?, ?, ?, ?)");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      statement.setBytes(5, record.serialize());
      statement.executeUpdate();
    } catch (SQLException | IOException e) {
      logger.catching(e);
    }
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress address, UUID distributionId) {
    try {
      Recipient recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT +
                                                                        " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?");
      statement.setString(1, aci.toString());
      statement.setInt(2, recipient.getId());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      ResultSet rows = statement.executeQuery();
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
}
