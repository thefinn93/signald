package io.finn.signald.db;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

class MessageQueueTableTest {
  private static final UUID ACCOUNT_UUID = UUID.fromString("00000000-0000-4000-0000-000000000000");
  private static final int TYPE_UNIDENTIFIED_SENDER = 6;

  private final MessageQueueTable messageQueue = new MessageQueueTable(ACCOUNT_UUID);

  @BeforeEach
  void setUp() throws IOException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    File databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

    Flyway flyway = Flyway.configure().dataSource(db, null, null).load();
    flyway.migrate();

    Database.setConnectionString(db);
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

    SignalServiceEnvelope storedEnvelope = messageQueue.nextEnvelope();

    assertEquals(type, storedEnvelope.getType());
    assertEquals(senderDevice, storedEnvelope.getSourceDevice());
    assertEquals(timestamp, storedEnvelope.getTimestamp());
    assertArrayEquals(legacyMessage, storedEnvelope.getLegacyMessage());
    assertArrayEquals(content, storedEnvelope.getContent());
    assertEquals(serverReceivedTimestamp, storedEnvelope.getServerReceivedTimestamp());
    assertEquals(serverDeliveredTimestamp, storedEnvelope.getServerDeliveredTimestamp());
    assertEquals(uuid, storedEnvelope.getServerGuid());

    // This is somewhat unexpected given a type of Optional<String>.
    // But it's consistent with deserialized Protobuf objects.
    assertEquals("", storedEnvelope.getSourceE164().get());
    assertEquals("", storedEnvelope.getSourceUuid().get());
  }

  @Test
  @DisplayName("deleteEnvelope() should only remove one entry from the message queue")
  void deleteEnvelope_onlyOneRow() throws SQLException {
    byte[] content1 = {1};
    byte[] content2 = {2};
    SignalServiceEnvelope envelope1 = createUnidentifiedSenderSignalServiceEnvelope(content1);
    SignalServiceEnvelope envelope2 = createUnidentifiedSenderSignalServiceEnvelope(content2);
    messageQueue.storeEnvelope(envelope1);
    messageQueue.storeEnvelope(envelope2);

    SignalServiceEnvelope storedEnvelope = messageQueue.nextEnvelope();
    messageQueue.deleteEnvelope(storedEnvelope);

    SignalServiceEnvelope secondStoredEnvelope = messageQueue.nextEnvelope();
    assertNotNull(secondStoredEnvelope, "Expected second envelope not found in message queue");
    assertArrayEquals(content2, secondStoredEnvelope.getContent());
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
