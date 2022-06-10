/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import static io.finn.signald.db.Database.Type.POSTGRESQL;
import static io.finn.signald.db.Database.Type.SQLITE;

import io.finn.signald.BuildConfig;
import io.finn.signald.Config;
import io.finn.signald.exceptions.InvalidProxyException;
import io.prometheus.client.Histogram;
import java.net.URI;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class Database {
  private static final Logger logger = LogManager.getLogger();
  private static final Histogram queryLatency =
      Histogram.build().name(BuildConfig.NAME + "_sqlite_query_latency_seconds").help("sqlite latency in seconds.").labelNames("query", "write").register();

  private static final Map<ServiceId, Database> DatabaseInstances = new HashMap<>();

  public static Database Get() { return Get(ACI.from(UuidUtil.UNKNOWN_UUID)); }
  public static Database Get(ACI aci) {
    synchronized (DatabaseInstances) {
      if (!DatabaseInstances.containsKey(aci)) {
        DatabaseInstances.put(aci, new Database(aci, GetConnectionType()));
      }
      return DatabaseInstances.get(aci);
    }
  }

  public enum Type { SQLITE, POSTGRESQL }

  public final IAccountDataTable AccountDataTable;
  public final IAccountsTable AccountsTable;
  public final IContactsTable ContactsTable;
  public final IGroupCredentialsTable GroupCredentialsTable;
  public final IGroupsTable GroupsTable;
  public final IIdentityKeysTable IdentityKeysTable;
  public final IMessageQueueTable MessageQueueTable;
  public final IPendingAccountDataTable PendingAccountDataTable;
  public final IPreKeysTable PreKeysTable;
  public final IRecipientsTable RecipientsTable;
  public final ISenderKeySharedTable SenderKeySharedTable;
  public final ISenderKeysTable SenderKeysTable;
  public final IServersTable ServersTable;
  public final ISessionsTable SessionsTable;
  public final ISignedPreKeysTable SignedPreKeysTable;
  public final IProfilesTable ProfilesTable;
  public final IProfileKeysTable ProfileKeysTable;
  public final IProfileCapabilitiesTable ProfileCapabilitiesTable;
  public final IProfileBadgesTable ProfileBadgesTable;
  private Database(ACI aci, Type databaseType) {
    switch (databaseType) {
    case SQLITE:
      AccountDataTable = new io.finn.signald.db.sqlite.AccountDataTable();
      AccountsTable = new io.finn.signald.db.sqlite.AccountsTable();
      ContactsTable = new io.finn.signald.db.sqlite.ContactsTable(aci);
      GroupCredentialsTable = new io.finn.signald.db.sqlite.GroupCredentialsTable(aci);
      GroupsTable = new io.finn.signald.db.sqlite.GroupsTable(aci);
      IdentityKeysTable = new io.finn.signald.db.sqlite.IdentityKeysTable(aci);
      MessageQueueTable = new io.finn.signald.db.sqlite.MessageQueueTable(aci);
      PendingAccountDataTable = new io.finn.signald.db.sqlite.PendingAccountDataTable();
      PreKeysTable = new io.finn.signald.db.sqlite.PreKeysTable(aci);
      RecipientsTable = new io.finn.signald.db.sqlite.RecipientsTable(aci);
      SenderKeySharedTable = new io.finn.signald.db.sqlite.SenderKeySharedTable(aci);
      SenderKeysTable = new io.finn.signald.db.sqlite.SenderKeysTable(aci);
      ServersTable = new io.finn.signald.db.sqlite.ServersTable();
      SessionsTable = new io.finn.signald.db.sqlite.SessionsTable(aci);
      SignedPreKeysTable = new io.finn.signald.db.sqlite.SignedPreKeysTable(aci);
      ProfilesTable = new io.finn.signald.db.sqlite.ProfilesTable(aci);
      ProfileKeysTable = new io.finn.signald.db.sqlite.ProfileKeysTable(aci);
      ProfileCapabilitiesTable = new io.finn.signald.db.sqlite.ProfileCapabilitiesTable(aci);
      ProfileBadgesTable = new io.finn.signald.db.sqlite.ProfileBadgesTable(aci);
      break;
    case POSTGRESQL:
      AccountDataTable = new io.finn.signald.db.postgresql.AccountDataTable();
      AccountsTable = new io.finn.signald.db.postgresql.AccountsTable();
      ContactsTable = new io.finn.signald.db.postgresql.ContactsTable(aci);
      GroupCredentialsTable = new io.finn.signald.db.postgresql.GroupCredentialsTable(aci);
      GroupsTable = new io.finn.signald.db.postgresql.GroupsTable(aci);
      IdentityKeysTable = new io.finn.signald.db.postgresql.IdentityKeysTable(aci);
      MessageQueueTable = new io.finn.signald.db.postgresql.MessageQueueTable(aci);
      PendingAccountDataTable = new io.finn.signald.db.postgresql.PendingAccountDataTable();
      PreKeysTable = new io.finn.signald.db.postgresql.PreKeysTable(aci);
      RecipientsTable = new io.finn.signald.db.postgresql.RecipientsTable(aci);
      SenderKeySharedTable = new io.finn.signald.db.postgresql.SenderKeySharedTable(aci);
      SenderKeysTable = new io.finn.signald.db.postgresql.SenderKeysTable(aci);
      ServersTable = new io.finn.signald.db.postgresql.ServersTable();
      SessionsTable = new io.finn.signald.db.postgresql.SessionsTable(aci);
      SignedPreKeysTable = new io.finn.signald.db.postgresql.SignedPreKeysTable(aci);
      ProfilesTable = new io.finn.signald.db.postgresql.ProfilesTable(aci);
      ProfileKeysTable = new io.finn.signald.db.postgresql.ProfileKeysTable(aci);
      ProfileCapabilitiesTable = new io.finn.signald.db.postgresql.ProfileCapabilitiesTable(aci);
      ProfileBadgesTable = new io.finn.signald.db.postgresql.ProfileBadgesTable(aci);
      break;
    default:
      throw new IllegalArgumentException("Illegal database type");
    }
  }

  private static Optional<Type> connectionType = Optional.empty();
  public static Type GetConnectionType() {
    if (connectionType.isEmpty()) {
      var connectionString = Config.getDb().substring(5);
      URI uri = URI.create(connectionString);
      switch (uri.getScheme()) {
      case "sqlite":
        connectionType = Optional.of(SQLITE);
        break;
      case "postgresql":
        connectionType = Optional.of(POSTGRESQL);
        break;
      default:
        throw new IllegalArgumentException("Invalid database scheme: " + uri.getScheme());
      }
    }
    return connectionType.get();
  }

  private static Connection conn;
  public static Connection getConn() throws SQLException {
    if (conn == null || conn.isClosed()) {
      close();

      switch (GetConnectionType()) {
      case SQLITE:
        conn = DriverManager.getConnection(Config.getDb());
        break;
      case POSTGRESQL:
        conn = DriverManager.getConnection(Config.getDb(), Config.getDbUser(), Config.getDbPassword());
        // per this one-vote non-accepted answer on stackoverflow, pg ignores the first param: https://stackoverflow.com/a/56257826
        conn.setNetworkTimeout(null, Config.getDBTimeout());
        break;
      default:
        throw new AssertionError("unsupported database type");
      }
    }
    return conn;
  }

  public static void close() {
    try {
      if (conn != null) {
        conn.close();
      }
    } catch (SQLException e) {
      logger.warn("Failed to close database connection", e);
    }
    connectionType = Optional.empty();
    conn = null;
  }

  // Methods that require switching per connection type
  public static void DeleteAccount(ACI aci, String legacyUsername) throws SQLException {
    switch (GetConnectionType()) {
    case SQLITE:
      new io.finn.signald.db.sqlite.AccountsTable().DeleteAccount(aci, legacyUsername);
      break;
    case POSTGRESQL:
      new io.finn.signald.db.postgresql.AccountsTable().DeleteAccount(aci, legacyUsername);
      break;
    }
  }

  public static IServersTable.AbstractServer NewServer(UUID uuid, String serviceURL, Map<Integer, String> cdns, String contactDiscoveryURL, String keyBackupURL, String storageURL,
                                                       byte[] zkParams, byte[] unidentifiedSenderRoot, String proxy, byte[] ca, String keyBackupServiceName,
                                                       byte[] keyBackupServiceId, String keyBackupMrenclave, String cdsMrenclave, byte[] iasCa) throws InvalidProxyException {
    switch (GetConnectionType()) {
    case SQLITE:
      return new io.finn.signald.db.sqlite.ServersTable.Server(uuid, serviceURL, cdns, contactDiscoveryURL, keyBackupURL, storageURL, zkParams, unidentifiedSenderRoot, proxy, ca,
                                                               keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, iasCa, "");
    case POSTGRESQL:
      return new io.finn.signald.db.postgresql.ServersTable.Server(uuid, serviceURL, cdns, contactDiscoveryURL, keyBackupURL, storageURL, zkParams, unidentifiedSenderRoot, proxy,
                                                                   ca, keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, iasCa, "");
    default:
      throw new IllegalArgumentException("Invalid connection type");
    }
  }

  // Helpers for executing queries
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
