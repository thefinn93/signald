/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import static org.junit.jupiter.api.Assertions.*;

import io.finn.signald.Config;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

class MessageQueueTableTest {
  private static final ACI ACCOUNT_ACI = ACI.from(UUID.fromString("00000000-0000-4000-0000-000000000000"));
  private static final int TYPE_UNIDENTIFIED_SENDER = 6;

  private IMessageQueueTable messageQueue;
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "sqlite:" + databaseFile.getAbsolutePath();

    Flyway flyway = Flyway.configure().locations("db/migration/sqlite").dataSource("jdbc:" + db, null, null).load();
    flyway.migrate();

    Config.testInit(db);
    messageQueue = Database.Get(ACCOUNT_ACI).MessageQueueTable;
  }

  @AfterEach
  void tearDown() {
    Database.close();
    if (!databaseFile.delete()) {
      System.err.println("Test database file couldn't be deleted: " + databaseFile.getAbsolutePath());
    }
  }

  @Test
  @DisplayName("nextEnvelope() with unidentified sender type")
  void nextEnvelope_withUnidentifiedSender() throws SQLException {
    int type = TYPE_UNIDENTIFIED_SENDER;
    Optional<SignalServiceAddress> sender = Optional.absent();
    int senderDevice = 0;
    long timestamp = 100L;
    byte[] legacyMessage = {};
    byte[] content = {1};
    long serverReceivedTimestamp = 200L;
    long serverDeliveredTimestamp = 300L;
    String uuid = UUID.randomUUID().toString();

    SignalServiceEnvelope originalEnvelope =
        new SignalServiceEnvelope(type, sender, senderDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid);
    messageQueue.storeEnvelope(originalEnvelope);

    StoredEnvelope storedEnvelope = messageQueue.nextEnvelope();

    SignalServiceEnvelope envelope = storedEnvelope.envelope;
    assertEquals(type, envelope.getType());
    assertEquals(senderDevice, envelope.getSourceDevice());
    assertEquals(timestamp, envelope.getTimestamp());
    assertArrayEquals(legacyMessage, envelope.getLegacyMessage());
    assertArrayEquals(content, envelope.getContent());
    assertEquals(serverReceivedTimestamp, envelope.getServerReceivedTimestamp());
    assertEquals(serverDeliveredTimestamp, envelope.getServerDeliveredTimestamp());
    assertEquals(uuid, envelope.getServerGuid());

    // This is somewhat unexpected given a type of Optional<String>.
    // But it's consistent with deserialized Protobuf objects.
    assertEquals("", envelope.getSourceE164().get());
    assertEquals("", envelope.getSourceUuid().get());
  }

  @Test
  @DisplayName("deleteEnvelope() should only remove one entry from the message queue")
  void deleteEnvelope_onlyOneRow() throws SQLException {
    byte[] content1 = {1};
    byte[] content2 = {2};
    SignalServiceEnvelope envelope1 = createUnidentifiedSenderSignalServiceEnvelope(content1);
    SignalServiceEnvelope envelope2 = createUnidentifiedSenderSignalServiceEnvelope(content2);
    long databaseId1 = messageQueue.storeEnvelope(envelope1);
    long databaseId2 = messageQueue.storeEnvelope(envelope2);

    StoredEnvelope storedEnvelope = messageQueue.nextEnvelope();
    assertEquals(databaseId1, storedEnvelope.databaseId);
    messageQueue.deleteEnvelope(storedEnvelope.databaseId);

    StoredEnvelope secondStoredEnvelope = messageQueue.nextEnvelope();
    assertNotNull(secondStoredEnvelope, "Expected second envelope not found in message queue");
    assertEquals(databaseId2, secondStoredEnvelope.databaseId);
    assertArrayEquals(content2, secondStoredEnvelope.envelope.getContent());
  }

  private SignalServiceEnvelope createUnidentifiedSenderSignalServiceEnvelope(byte[] content) {
    Optional<SignalServiceAddress> sender = Optional.absent();
    int senderDevice = 0;
    long timestamp = 100L;
    byte[] legacyMessage = {};
    long serverReceivedTimestamp = 200L;
    long serverDeliveredTimestamp = 300L;
    String uuid = UUID.randomUUID().toString();

    return new SignalServiceEnvelope(TYPE_UNIDENTIFIED_SENDER, sender, senderDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid);
  }
}
