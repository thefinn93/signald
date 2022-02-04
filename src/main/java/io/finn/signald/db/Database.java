/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.BuildConfig;
import io.finn.signald.Config;
import io.prometheus.client.Histogram;
import java.sql.*;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Database {
  private static final Logger logger = LogManager.getLogger();
  private static final Histogram queryLatency =
      Histogram.build().name(BuildConfig.NAME + "_sqlite_query_latency_seconds").help("sqlite latency in seconds.").labelNames("query", "write").register();
  private static Connection conn;

  private final UUID uuid;

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

  public static ResultSet executeQuery(String name, PreparedStatement statement) throws SQLException {
    Histogram.Timer timer = queryLatency.labels(name, "false").startTimer();
    try {
      return statement.executeQuery();
    } finally {
      double seconds = timer.observeDuration();
      if (Config.getLogDatabaseTransactions()) {
        logger.debug("executed query {} in {} ms", name, seconds * 1000);
      }
    }
  }

  public static int executeUpdate(String name, PreparedStatement statement) throws SQLException {
    Histogram.Timer timer = queryLatency.labels(name, "true").startTimer();
    try {
      return statement.executeUpdate();
    } finally {
      double seconds = timer.observeDuration();
      if (Config.getLogDatabaseTransactions()) {
        logger.debug("executed query {} in {} ms", name, seconds * 1000);
      }
    }
  }

  public static ResultSet getGeneratedKeys(String name, PreparedStatement statement) throws SQLException {
    Histogram.Timer timer = queryLatency.labels(name, "true").startTimer();
    try {
      return statement.getGeneratedKeys();
    } finally {
      double seconds = timer.observeDuration();
      if (Config.getLogDatabaseTransactions()) {
        logger.debug("executed query {} in {} ms", name, seconds * 1000);
      }
    }
  }

  public static int[] executeBatch(String name, PreparedStatement statement) throws SQLException {
    Histogram.Timer timer = queryLatency.labels(name, "true").startTimer();
    try {
      return statement.executeBatch();
    } finally {
      double seconds = timer.observeDuration();
      if (Config.getLogDatabaseTransactions()) {
        logger.debug("executed query {} in {} ms", name, seconds * 1000);
      }
    }
  }
}
