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
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SenderKeysTable implements SenderKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String RECIPIENT = "recipient";
  private static final String DEVICE = "device";
  private static final String DISTRIBUTION_ID = "distribution_id";
  private static final String RECORD = "record";

  private final UUID accountId;
  private final RecipientsTable recipientsTable;

  public SenderKeysTable(UUID uuid) {
    accountId = uuid;
    recipientsTable = new RecipientsTable(uuid);
  }

  @Override
  public void storeSenderKey(SignalProtocolAddress address, UUID distributionId, SenderKeyRecord record) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + RECIPIENT + "," + DEVICE + "," +
                                                                        DISTRIBUTION_ID + "," + RECORD + ") VALUES (?, ?, ?, ?, ?)");
      statement.setString(1, accountId.toString());
      statement.setInt(2, recipient.first());
      statement.setInt(3, address.getDeviceId());
      statement.setString(4, distributionId.toString());
      statement.setBytes(5, record.serialize());
      statement.executeUpdate();
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress address, UUID distributionId) {
    try {
      Pair<Integer, SignalServiceAddress> recipient = recipientsTable.get(address.getName());
      PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + RECORD + " FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + RECIPIENT +
                                                                        " = ? AND " + DEVICE + " = ? AND " + DISTRIBUTION_ID + " = ?");
      statement.setString(1, accountId.toString());
      statement.setInt(2, recipient.first());
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
