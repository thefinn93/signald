/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.sqlite;

import static org.junit.jupiter.api.Assertions.*;

import io.finn.signald.db.Database;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.TestUtil;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
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

  private IRecipientsTable recipientsTable;
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException, SQLException {
    databaseFile = TestUtil.createAndConfigureTestSQLiteDatabase();
    recipientsTable = Database.Get(ACI.from(SELF_ADDRESS.getServiceId().uuid())).RecipientsTable;
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
    assertEquals(r.getServiceId(), ADDRESS_A.getServiceId());
  }

  @Test
  @DisplayName("get recipient by uuid")
  void get_uuid() throws SQLException, IOException {
    Recipient r = recipientsTable.get(ADDRESS_A.getServiceId());
    assertTrue(r.getAddress().getNumber().isPresent());
    assertEquals(r.getAddress().getNumber().get(), ADDRESS_A.getNumber().get());
  }

  @Test
  @DisplayName("get non-existent recipient by uuid")
  void get_nonExistentByUUID() throws IOException, SQLException {
    Recipient r = recipientsTable.get(ADDRESS_B.getServiceId());
    assertEquals(r.getServiceId(), ADDRESS_B.getServiceId());
    assertFalse(r.getAddress().getNumber().isPresent());
  }

  @Test
  @DisplayName("ensure all recipients are registered by default")
  void get_registered() throws SQLException, IOException {
    assertTrue(recipientsTable.get(ADDRESS_B.getServiceId()).isRegistered());
  }

  @Test
  @DisplayName("mark a recipient as unregistered")
  void setRegistrationStatus() throws SQLException, IOException {
    Recipient r = recipientsTable.get(ADDRESS_A.getServiceId());
    recipientsTable.setRegistrationStatus(r, false);

    assertFalse(recipientsTable.get(ADDRESS_A.getServiceId()).isRegistered());
  }
}
