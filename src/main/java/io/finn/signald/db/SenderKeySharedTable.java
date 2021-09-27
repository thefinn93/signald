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

import io.finn.signald.Account;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.SignalServiceDataStore;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeySharedTable {
  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "sender_keys";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String DISTRIBUTION_ID = "distribution_id";
  private static final String DEVICE = "device";
  private static final String ADDRESS = "address";

  private final UUID accountUUID;
  private final RecipientsTable recipientsTable;

  public SenderKeySharedTable(UUID uuid) {
    accountUUID = uuid;
    recipientsTable = new RecipientsTable(uuid);
  }

  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    Set<SignalProtocolAddress> addresses = new HashSet<>();
    try {
      PreparedStatement statement =
          Database.getConn().prepareStatement("SELECT " + ADDRESS + "," + DEVICE + ") FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + DISTRIBUTION_ID + " = ?");
      statement.setString(1, accountUUID.toString());
      statement.setString(2, distributionId.toString());

      ResultSet rows = statement.executeQuery();
      while (rows.next()) {
        SignalProtocolAddress address = new SignalProtocolAddress(rows.getString(ADDRESS), rows.getInt(DEVICE));
        addresses.add(address);
      }
    } catch (SQLException e) {
      logger.catching(e);
    }
    return addresses;
  }

  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("INSERT OR REPLACE INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + ADDRESS + "," + DEVICE + "," +
                                                                        DISTRIBUTION_ID + ") VALUES (?, ?, ?)");
      for (SignalProtocolAddress address : addresses) {
        statement.setString(1, accountUUID.toString());
        statement.setString(2, address.getName());
        statement.setInt(3, address.getDeviceId());
        statement.setString(4, distributionId.toString());
        statement.addBatch();
      }
      statement.executeBatch();
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  public void clearSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    try {
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + ADDRESS + " = ? AND " + DEVICE +
                                                                        " = ? AND " + DISTRIBUTION_ID + " = ?");
      for (SignalProtocolAddress address : addresses) {
        statement.setString(1, accountUUID.toString());
        statement.setString(2, distributionId.toString());
        statement.setInt(3, address.getDeviceId());
        statement.setString(4, distributionId.toString());
        statement.addBatch();
      }
      statement.executeBatch();
    } catch (SQLException e) {
      logger.catching(e);
    }
  }

  public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> collection) {}

  public boolean isMultiDevice() { return new Account(accountUUID).getMultiDevice(); }

  public SignalServiceDataStore.Transaction beginTransaction() {
    return ()
               -> {
                   // No-op transaction should be safe, as it's only a performance improvement
                   // this is what signal-cli does, we should investigate eventually
               };
  }
}
