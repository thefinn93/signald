/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.Config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Database {
  private static final Logger logger = LogManager.getLogger();
  private static Connection conn;

  private UUID uuid;

  public static Connection getConn() throws SQLException {
    if (conn == null) {
      conn = DriverManager.getConnection(Config.getDb());
    }
    return conn;
  }

  public static void close() {
    try {
      conn.close();
    } catch (SQLException e) {
      logger.warn("Failed to close database connection", e);
    }
    conn = null;
  }

  public Database(UUID u) { uuid = u; }

  public MessageQueueTable getMessageQueueTable() { return new MessageQueueTable(uuid); }
}
