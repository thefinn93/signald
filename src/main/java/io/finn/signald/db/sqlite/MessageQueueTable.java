/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.IMessageQueueTable;
import io.finn.signald.db.StoredEnvelope;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class MessageQueueTable implements IMessageQueueTable {
  private static final Logger logger = LogManager.getLogger();
  private static final String TABLE_NAME = "message_queue";

  private final ACI aci;

  public MessageQueueTable(ACI aci) { this.aci = aci; }

  @Override
  public long storeEnvelope(SignalServiceEnvelope envelope) throws SQLException {
    var query = "INSERT INTO " + TABLE_NAME + " (" + ACCOUNT + ", " + VERSION + ", " + TYPE + ", " + SOURCE_E164 + ", " + SOURCE_UUID + ", " + SOURCE_DEVICE + ", " + TIMESTAMP +
                ", " + CONTENT + ", " + LEGACY_MESSAGE + ", " + SERVER_RECEIVED_TIMESTAMP + ", " + SERVER_DELIVERED_TIMESTAMP + ", " + SERVER_UUID +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      statement.setInt(2, 2); // Version is hard-coded to 2
      statement.setInt(3, envelope.getType());
      if (envelope.getSourceE164().isPresent()) {
        statement.setString(4, envelope.getSourceE164().get());
      }
      if (envelope.getSourceUuid().isPresent()) {
        statement.setString(5, envelope.getSourceUuid().get());
      }
      statement.setInt(6, envelope.getSourceDevice());
      statement.setLong(7, envelope.getTimestamp());
      if (envelope.hasContent()) {
        statement.setBytes(8, envelope.getContent());
      }
      if (envelope.hasLegacyMessage()) {
        statement.setBytes(9, envelope.getLegacyMessage());
      }
      statement.setLong(10, envelope.getServerReceivedTimestamp());
      statement.setLong(11, envelope.getServerDeliveredTimestamp());
      statement.setString(12, envelope.getServerGuid());
      statement.executeUpdate();

      try (var generatedKeys = Database.getGeneratedKeys(TABLE_NAME + "_store_envelope", statement)) {
        generatedKeys.next();
        return generatedKeys.getLong(1);
      }
    }
  }

  @Override
  public void deleteEnvelope(long id) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ID + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setLong(1, id);
      Database.executeUpdate(TABLE_NAME + "_delete_envelope", statement);
    }
  }

  @Override
  public StoredEnvelope nextEnvelope() throws SQLException {
    var query = "SELECT * FROM " + TABLE_NAME + " WHERE " + ACCOUNT + " = ? ORDER BY " + ID + " LIMIT 1";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, aci.toString());
      try (var rows = Database.executeQuery(TABLE_NAME + "_next_envelope", statement)) {
        if (!rows.next()) {
          return null;
        }
        long id = rows.getLong(ID);
        int type = rows.getInt(TYPE);
        Optional<SignalServiceAddress> sender = Optional.absent();
        String senderE164 = rows.getString(SOURCE_E164);
        String senderUUIDString = rows.getString(SOURCE_UUID);
        if ((senderE164 != null && senderE164.length() > 0) || (senderUUIDString != null && senderUUIDString.length() > 0)) {
          ACI senderACI = (senderUUIDString != null && senderUUIDString.length() > 0) ? ACI.from(UUID.fromString(senderUUIDString)) : null;
          sender = Optional.of(new SignalServiceAddress(senderACI, senderE164));
        }
        int senderDevice = rows.getInt(SOURCE_DEVICE);
        long timestamp = rows.getLong(TIMESTAMP);
        byte[] legacyMessage = rows.getBytes(LEGACY_MESSAGE);
        byte[] content = rows.getBytes(CONTENT);
        long serverReceivedTimestamp = rows.getLong(SERVER_RECEIVED_TIMESTAMP);
        long serverDeliveredTimestamp = rows.getLong(SERVER_DELIVERED_TIMESTAMP);
        String uuid = rows.getString(SERVER_UUID);
        SignalServiceEnvelope signalServiceEnvelope =
            new SignalServiceEnvelope(type, sender, senderDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid);
        return new StoredEnvelope(id, signalServiceEnvelope);
      }
    }
  }

  @Override
  public void deleteAccount(String account) throws SQLException {
    var query = "DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT + " = ?";
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setString(1, account);
      Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
    }
  }
}
