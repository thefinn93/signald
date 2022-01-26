/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class RecipientTableTest {
  private static final SignalServiceAddress SELF_ADDRESS = new SignalServiceAddress(ACI.from(UUID.fromString("00000000-0000-4000-0000-000000000000")), "+12025551212");

  private static final SignalServiceAddress ADDRESS_A = new SignalServiceAddress(ACI.from(UUID.fromString("356db4e7-5a90-4574-81ab-52340ae4218d")), "+12024561414");
  private static final SignalServiceAddress ADDRESS_B = new SignalServiceAddress(ACI.from(UUID.fromString("ede05132-54e8-4cbc-bf5d-75204c9415da")));

  private final RecipientsTable recipientsTable = new RecipientsTable(SELF_ADDRESS.getAci());
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException, SQLException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

    Flyway flyway = Flyway.configure().dataSource(db, null, null).load();
    flyway.migrate();

    Config.testInit(db);

    recipientsTable.get(SELF_ADDRESS);
    recipientsTable.get(ADDRESS_A);
  }

  @AfterEach
  void tearDown() {
    Database.close();
    if (!databaseFile.delete()) {
      System.err.println("Test database file couldn't be deleted: " + databaseFile.getAbsolutePath());
    }
  }

  @Test
  @DisplayName("get recipient by e164")
  void get_e164() throws SQLException, IOException {
    Recipient r = recipientsTable.get(ADDRESS_A.getNumber().get());
    assertEquals(r.getACI(), ADDRESS_A.getAci());
  }

  @Test
  @DisplayName("get recipient by uuid")
  void get_uuid() throws SQLException, IOException {
    Recipient r = recipientsTable.get(ADDRESS_A.getAci());
    assertEquals(r.getAddress().getNumber().orNull(), ADDRESS_A.getNumber().orNull());
  }

  @Test
  @DisplayName("get non-existant recipient by uuid")
  void get_nonExistentByUUID() throws IOException, SQLException {
    Recipient r = recipientsTable.get(ADDRESS_B.getAci());
    assertEquals(r.getACI(), ADDRESS_B.getAci());
    assertFalse(r.getAddress().getNumber().isPresent());
  }
}
