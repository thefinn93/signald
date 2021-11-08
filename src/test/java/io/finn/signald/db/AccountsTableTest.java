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

    Database.setConnectionString(db);
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
