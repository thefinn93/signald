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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public class Database {
  private static final Logger logger = LogManager.getLogger();
  private static String connectionString;
  private static Connection conn;

  private UUID uuid;

  public static void setConnectionString(String c) { connectionString = c; }

  public static Connection getConn() throws SQLException {
    if (conn == null) {
      conn = DriverManager.getConnection(connectionString);
    }
    return conn;
  }

  public Database(UUID u) { uuid = u; }

  public MessageQueueTable getMessageQueueTable() { return new MessageQueueTable(uuid); }
}
