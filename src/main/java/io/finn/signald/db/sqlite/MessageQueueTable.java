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
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
                ", " + CONTENT + ", " + SERVER_RECEIVED_TIMESTAMP + ", " + SERVER_DELIVERED_TIMESTAMP + ", " + SERVER_UUID + ", " + DESTINATION_UUID + ", " + URGENT + ", " +
                UPDATED_PNI + ", " + STORY + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    try (var statement = Database.getConn().prepareStatement(query)) {
      int i = 1;
      statement.setString(i++, aci.toString());
      statement.setInt(i++, 2); // Version is hard-coded to 2
      statement.setInt(i++, envelope.getType());
      statement.setString(i++, envelope.getSourceIdentifier());
      if (envelope.getSourceUuid().isPresent()) {
        statement.setString(i++, envelope.getSourceUuid().get());
      }
      statement.setInt(i++, envelope.getSourceDevice());
      statement.setLong(i++, envelope.getTimestamp());
      if (envelope.hasContent()) {
        statement.setBytes(i++, envelope.getContent());
      }
      statement.setLong(i++, envelope.getServerReceivedTimestamp());
      statement.setLong(i++, envelope.getServerDeliveredTimestamp());
      statement.setString(i++, envelope.getServerGuid());
      statement.setString(i++, envelope.getDestinationUuid());
      statement.setBoolean(i++, envelope.isUrgent());
      statement.setString(i++, envelope.getUpdatedPni());
      statement.setBoolean(i++, envelope.isStory());
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
        Optional<SignalServiceAddress> sender = Optional.empty();
        String senderE164 = rows.getString(SOURCE_E164);
        String senderUUIDString = rows.getString(SOURCE_UUID);
        if ((senderE164 != null && senderE164.length() > 0) || (senderUUIDString != null && senderUUIDString.length() > 0)) {
          ACI senderACI = (senderUUIDString != null && senderUUIDString.length() > 0) ? ACI.from(UUID.fromString(senderUUIDString)) : null;
          sender = Optional.of(new SignalServiceAddress(senderACI, senderE164));
        }
        int senderDevice = rows.getInt(SOURCE_DEVICE);
        long timestamp = rows.getLong(TIMESTAMP);
        byte[] content = rows.getBytes(CONTENT);
        long serverReceivedTimestamp = rows.getLong(SERVER_RECEIVED_TIMESTAMP);
        long serverDeliveredTimestamp = rows.getLong(SERVER_DELIVERED_TIMESTAMP);
        String uuid = rows.getString(SERVER_UUID);
        String destinationUUID = rows.getString(DESTINATION_UUID);
        boolean urgent = rows.getBoolean(URGENT);
        String updatedPni = rows.getString(UPDATED_PNI);
        boolean story = rows.getBoolean(STORY);
        SignalServiceEnvelope signalServiceEnvelope = new SignalServiceEnvelope(type, sender, senderDevice, timestamp, content, serverReceivedTimestamp, serverDeliveredTimestamp,
                                                                                uuid, destinationUUID, urgent, updatedPni, story);
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
