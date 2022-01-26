/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.Config;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.whispersystems.signalservice.api.push.ACI;

public class AccountsTableTest {
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

    Flyway flyway = Flyway.configure().dataSource(db, null, null).load();
    flyway.migrate();

    Config.testInit(db);
  }

  @AfterEach
  void tearDown() {
    Database.close();
    if (!databaseFile.delete()) {
      System.err.println("Test database file couldn't be deleted: " + databaseFile.getAbsolutePath());
    }
  }

  @Test
  @DisplayName("try to get e164 of non-existent account")
  void getE164_NoSuchAccountException() {
    Assertions.assertThrows(NoSuchAccountException.class, () -> { AccountsTable.getE164(ACI.from(UUID.randomUUID())); });
  }

  @Test
  @DisplayName("try to get UUID of non-existent account")
  void getUUID_NoSuchAccountException() {
    Assertions.assertThrows(NoSuchAccountException.class, () -> { AccountsTable.getUUID("+12025551212"); });
  }
}
