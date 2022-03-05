/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.TestUtil;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.whispersystems.signalservice.api.push.ACI;

public class AccountsTableTest {
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException {
    databaseFile = TestUtil.createAndConfigureTestSQLiteDatabase();
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
    Assertions.assertThrows(NoSuchAccountException.class, () -> Database.Get().AccountsTable.getE164(ACI.from(UUID.randomUUID())));
  }

  @Test
  @DisplayName("try to get UUID of non-existent account")
  void getUUID_NoSuchAccountException() {
    Assertions.assertThrows(NoSuchAccountException.class, () -> Database.Get().AccountsTable.getUUID("+12025551212"));
  }
}
