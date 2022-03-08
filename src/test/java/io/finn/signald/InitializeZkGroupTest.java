/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.util.GroupsUtil;
import java.io.File;
import java.io.IOException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InitializeZkGroupTest {
  private File databaseFile;

  @BeforeEach
  void setUp() throws IOException {
    File tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "sqlite:" + databaseFile.getAbsolutePath();

    Flyway flyway = Flyway.configure().locations("db/migration/sqlite").dataSource("jdbc:" + db, null, null).load();
    flyway.migrate();

    Config.testInit(db);
  }

  @Test
  @DisplayName("initialize the zkgroup library")
  void initializeZkGroup() throws IOException, InvalidProxyException {
    GroupsUtil.GetGroupsV2Operations(Database.Get().ServersTable.getDefaultServer().getSignalServiceConfiguration());
  }
}
