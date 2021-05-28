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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.sql.*;
import java.util.UUID;

public class MessageQueueTable {
  private static final Logger logger = LogManager.getLogger();
  private static final String TABLE_NAME = "message_queue";
  // column names
  private static final String ID = "id";
  private static final String ACCOUNT = "account";
  private static final String VERSION = "version";
  private static final String TYPE = "type";
  private static final String SOURCE_E164 = "source_e164";
  private static final String SOURCE_UUID = "source_uuid";
  private static final String SOURCE_DEVICE = "source_device";
  private static final String TIMESTAMP = "timestamp";
  private static final String CONTENT = "content";
  private static final String LEGACY_MESSAGE = "legacy_message";
  private static final String SERVER_RECEIVED_TIMESTAMP = "server_received_timestamp";
  private static final String SERVER_UUID = "server_uuid";
  private static final String SERVER_DELIVERED_TIMESTAMP = "server_delivered_timestamp";

  private final UUID uuid;

  public MessageQueueTable(UUID u) { uuid = u; }

  public void storeEnvelope(SignalServiceEnvelope envelope) throws SQLException {
    Connection conn = Database.getConn();
    PreparedStatement statement = conn.prepareStatement("INSERT INTO " + TABLE_NAME + " (" + ACCOUNT + ", " + VERSION + ", " + TYPE + ", " + SOURCE_E164 + ", " + SOURCE_UUID +
                                                        ", " + SOURCE_DEVICE + ", " + TIMESTAMP + ", " + CONTENT + ", " + LEGACY_MESSAGE + ", " + SERVER_RECEIVED_TIMESTAMP + ", " +
                                                        SERVER_DELIVERED_TIMESTAMP + ", " + SERVER_UUID + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
    statement.setString(1, uuid.toString());
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
  }

  public void deleteEnvelope(SignalServiceEnvelope envelope) throws SQLException {
    Connection conn = Database.getConn();
    PreparedStatement statement = conn.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + SOURCE_E164 + " = ? AND " + SOURCE_UUID + " = ? AND " + TIMESTAMP + " = ?");
    if (envelope.getSourceE164().isPresent()) {
      statement.setString(1, envelope.getSourceE164().get());
    }
    if (envelope.getSourceUuid().isPresent()) {
      statement.setString(2, envelope.getSourceUuid().get());
    }
    statement.setLong(3, envelope.getTimestamp());
    statement.executeUpdate();
  }

  public SignalServiceEnvelope nextEnvelope() throws SQLException {
    Connection conn = Database.getConn();
    PreparedStatement statement = conn.prepareStatement("SELECT * FROM " + TABLE_NAME + " WHERE " + ACCOUNT + " = ? LIMIT 1");
    statement.setString(1, uuid.toString());
    ResultSet rows = statement.executeQuery();
    if (!rows.next()) {
      rows.close();
      return null;
    }
    int type = rows.getInt(TYPE);
    Optional<SignalServiceAddress> sender = Optional.absent();
    String senderE164 = rows.getString(SOURCE_E164);
    String senderUUIDString = rows.getString(SOURCE_UUID);
    if ((senderE164 != null && senderE164.length() > 0) || (senderUUIDString != null && senderUUIDString.length() > 0)) {
      UUID senderUUID = (senderUUIDString != null && senderUUIDString.length() > 0) ? UUID.fromString(senderUUIDString) : null;
      sender = Optional.of(new SignalServiceAddress(senderUUID, senderE164));
    }
    int senderDevice = rows.getInt(SOURCE_DEVICE);
    long timestamp = rows.getLong(TIMESTAMP);
    byte[] legacyMessage = rows.getBytes(LEGACY_MESSAGE);
    byte[] content = rows.getBytes(CONTENT);
    long serverReceivedTimestamp = rows.getLong(SERVER_RECEIVED_TIMESTAMP);
    long serverDeliveredTimestamp = rows.getLong(SERVER_DELIVERED_TIMESTAMP);
    String uuid = rows.getString(SERVER_UUID);
    rows.close();
    return new SignalServiceEnvelope(type, sender, senderDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid);
  }

  public static void deleteAccount(String account) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT + " = ?");
    statement.setString(1, account);
    statement.executeUpdate();
  }
}
