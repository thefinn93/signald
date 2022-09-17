/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db.postgresql;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.BuildConfig;
import io.finn.signald.db.Database;
import io.finn.signald.db.IServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.JSONUtil;
import io.sentry.Sentry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;
import okhttp3.Interceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.util.Base64;

public class ServersTable implements IServersTable {
  private static final String TABLE_NAME = "signald_servers";

  private static final Logger logger = LogManager.getLogger();

  @Override
  public Server getDefaultServer() throws IOException, InvalidProxyException {
    HashMap<Integer, String> cdns = new HashMap<>();
    cdns.put(0, BuildConfig.SIGNAL_CDN_URL);
    // unclear why there is no CDN 1
    cdns.put(2, BuildConfig.SIGNAL_CDN2_URL);
    byte[] zkparam = Base64.decode(BuildConfig.SIGNAL_ZK_GROUP_SERVER_PUBLIC_PARAMS);
    byte[] unidentifiedSenderRoot = Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT);
    byte[] ca = Base64.decode(BuildConfig.CA);

    String keyBackupServiceName = BuildConfig.KEY_BACKUP_SERVICE_NAME;
    byte[] keyBackupServiceId = Base64.decode(BuildConfig.KEY_BACKUP_SERVICE_ID);
    String keyBackupMrenclave = BuildConfig.KEY_BACKUP_MRENCLAVE;
    String cdsMrenclave = BuildConfig.CDS_MRENCLAVE;
    byte[] cdsCa = Base64.decode(BuildConfig.CDS_CA);

    return new Server(DEFAULT_SERVER, BuildConfig.SIGNAL_URL, cdns, BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL, BuildConfig.SIGNAL_KEY_BACKUP_URL, BuildConfig.SIGNAL_STORAGE_URL,
                      zkparam, unidentifiedSenderRoot, BuildConfig.SIGNAL_PROXY, ca, keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, "");
  }

  @Override
  public AbstractServer getServer(UUID uuid) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    var query = String.format("SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s FROM %s WHERE %s=?",
                              // FIELDS
                              SERVER_UUID, SERVICE_URL, CDN_URLS, CONTACT_DISCOVERY_URL, KEY_BACKUP_URL, STORAGE_URL, ZK_GROUP_PUBLIC_PARAMS, UNIDENTIFIED_SENDER_ROOT, PROXY, CA,
                              KEY_BACKUP_SERVICE_NAME, KEY_BACKUP_SERVICE_ID, KEY_BACKUP_MRENCLAVE, CDS_MRENCLAVE, IAS_CA,
                              // FROM, WHERE
                              TABLE_NAME, SERVER_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, uuid);
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_server", statement)) {
        // If there are no rows returned, check if it's one of the preloaded servers
        if (!rows.next()) {
          if (DEFAULT_SERVER.equals(uuid)) {
            // if the default server isn't in the DB, create it
            Server server = getDefaultServer();
            create(server);
            return server;
          }

          throw new ServerNotFoundException(uuid);
        }

        UUID serverUUID = UUID.fromString(rows.getString(SERVER_UUID));
        String serviceURL = rows.getString(SERVICE_URL);
        String cdnURLs = rows.getString(CDN_URLS);
        String contactDiscoveryURL = rows.getString(CONTACT_DISCOVERY_URL);
        String keyBackupURL = rows.getString(KEY_BACKUP_URL);
        String storageURL = rows.getString(STORAGE_URL);
        byte[] zkGroupPublicParams = rows.getBytes(ZK_GROUP_PUBLIC_PARAMS);
        byte[] unidentifiedSenderRoot = rows.getBytes(UNIDENTIFIED_SENDER_ROOT);
        String proxyString = rows.getString(PROXY);
        byte[] ca = rows.getBytes(CA);
        String keyBackupServiceName = rows.getString(KEY_BACKUP_SERVICE_NAME);
        byte[] keyBackupServiceId = rows.getBytes(KEY_BACKUP_SERVICE_ID);
        String keyBackupMrenclave = rows.getString(KEY_BACKUP_MRENCLAVE);
        String cdsMrenclave = rows.getString(CDS_MRENCLAVE);
        byte[] cdsCa = rows.getBytes(IAS_CA);

        return new Server(serverUUID, serviceURL, cdnURLs, contactDiscoveryURL, keyBackupURL, storageURL, zkGroupPublicParams, unidentifiedSenderRoot, proxyString, ca,
                          keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, "");
      }
    }
  }

  @Override
  public List<AbstractServer> allServers() throws SQLException {
    List<AbstractServer> servers = new ArrayList<>();

    var query = String.format("SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s FROM %s",
                              // FIELDS
                              SERVER_UUID, SERVICE_URL, CDN_URLS, CONTACT_DISCOVERY_URL, KEY_BACKUP_URL, STORAGE_URL, ZK_GROUP_PUBLIC_PARAMS, UNIDENTIFIED_SENDER_ROOT, PROXY, CA,
                              KEY_BACKUP_SERVICE_NAME, KEY_BACKUP_SERVICE_ID, KEY_BACKUP_MRENCLAVE, CDS_MRENCLAVE, IAS_CA,
                              // FROM
                              TABLE_NAME);
    try (var statement = Database.getConn().prepareStatement(query)) {
      try (var rows = Database.executeQuery(TABLE_NAME + "_get_all_servers", statement)) {
        while (rows.next()) {
          UUID serverUUID = UUID.fromString(rows.getString(SERVER_UUID));
          String serviceURL = rows.getString(SERVICE_URL);
          String cdnURLs = rows.getString(CDN_URLS);
          String contactDiscoveryURL = rows.getString(CONTACT_DISCOVERY_URL);
          String keyBackupURL = rows.getString(KEY_BACKUP_URL);
          String storageURL = rows.getString(STORAGE_URL);
          byte[] zkGroupPublicParams = rows.getBytes(ZK_GROUP_PUBLIC_PARAMS);
          byte[] unidentifiedSenderRoot = rows.getBytes(UNIDENTIFIED_SENDER_ROOT);
          String proxyString = rows.getString(PROXY);
          byte[] ca = rows.getBytes(CA);
          String keyBackupServiceName = rows.getString(KEY_BACKUP_SERVICE_NAME);
          byte[] keyBackupServiceId = rows.getBytes(KEY_BACKUP_SERVICE_ID);
          String keyBackupMrenclave = rows.getString(KEY_BACKUP_MRENCLAVE);
          String cdsMrenclave = rows.getString(CDS_MRENCLAVE);
          byte[] cdsCa = rows.getBytes(IAS_CA);

          try {
            Server server = new Server(serverUUID, serviceURL, cdnURLs, contactDiscoveryURL, keyBackupURL, storageURL, zkGroupPublicParams, unidentifiedSenderRoot, proxyString, ca,
                                       keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, "");
            servers.add(server);
          } catch (IOException | InvalidProxyException e) {
            logger.warn("failed to load signal server " + serverUUID + " from database: " + e.getMessage());
          }
        }
        return servers;
      }
    }
  }

  @Override
  public void create(AbstractServer server) throws SQLException, JsonProcessingException {
    var query = String.format("INSERT INTO %s (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", TABLE_NAME,
                              // FIELDS
                              SERVER_UUID, SERVICE_URL, CDN_URLS, CONTACT_DISCOVERY_URL, KEY_BACKUP_URL, STORAGE_URL, ZK_GROUP_PUBLIC_PARAMS, UNIDENTIFIED_SENDER_ROOT, PROXY, CA);
    try (var statement = Database.getConn().prepareStatement(query)) {
      if (server.uuid == null) {
        server.uuid = UUID.randomUUID();
      }

      int i = 1;
      statement.setObject(i++, server.uuid);
      statement.setString(i++, server.serviceURL);
      statement.setString(i++, JSONUtil.GetMapper().writeValueAsString(server.cdnURLs));
      statement.setString(i++, server.contactDiscoveryURL);
      statement.setString(i++, server.keyBackupURL);
      statement.setString(i++, server.storageURL);
      statement.setBytes(i++, server.zkParams);
      statement.setBytes(i++, server.unidentifiedSenderRoot);
      if (server.proxy != null) {
        statement.setString(i++, server.proxy);
      } else {
        statement.setString(i++, null);
      }
      statement.setBytes(i, server.ca);
      Database.executeUpdate(TABLE_NAME + "_create", statement);
    }
  }

  @Override
  public void delete(UUID server)throws SQLException {
    var query = String.format("DELETE FROM %s WHERE %s=?", TABLE_NAME, SERVER_UUID);
    try (var statement = Database.getConn().prepareStatement(query)) {
      statement.setObject(1, server);
      Database.executeUpdate(TABLE_NAME + "_delete", statement);
    }
  }

  public static class Server extends AbstractServer {
    public Server(UUID uuid, String serviceURL, Map<Integer, String> cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParams,
                  byte[] unidentifiedSenderRoot, String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave, String cdsMrenclave,
                  byte[] iasCa, String cdshURL) throws InvalidProxyException {
      super(uuid, serviceURL, cdnURLs, contactDiscoveryURL, keyBackupURL, storageURL, zkParams, unidentifiedSenderRoot, proxy, ca, keyBackupServiceName, keyBackupServiceId,
            keyBackupMrenclave, cdsMrenclave, iasCa, cdshURL);
    }

    public Server(UUID uuid, String serviceURL, String cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParam, byte[] unidentifiedSenderRoot,
                  String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave, String cdsMrenclave, byte[] cdsCa, String cdshURL)
        throws IOException, InvalidProxyException {
      super(uuid, serviceURL, cdnURLs, contactDiscoveryURL, keyBackupURL, storageURL, zkParam, unidentifiedSenderRoot, proxy, ca, keyBackupServiceName, keyBackupServiceId,
            keyBackupMrenclave, cdsMrenclave, cdsCa, cdshURL);
    }

    @Override
    public TrustStore GetTrustStore(UUID uuid, String field) {
      return new DatabaseTrustStore(uuid, field);
    }
  }

  static class DatabaseTrustStore implements TrustStore {
    private final UUID uuid;
    private final String field;

    DatabaseTrustStore(UUID uuid, String field) {
      this.uuid = uuid;
      this.field = field;
    }

    @Override
    public InputStream getKeyStoreInputStream() {
      try {
        var query = String.format("SELECT %s FROM %s WHERE %s=?", field, TABLE_NAME, SERVER_UUID);
        try (var statement = Database.getConn().prepareStatement(query)) {
          statement.setObject(1, uuid);
          try (var rows = Database.executeQuery(TABLE_NAME + "_get_key", statement)) {
            if (!rows.next()) {
              logger.warn("this should never happen: no results for server when getting keystore");
              return null;
            }
            return new ByteArrayInputStream(rows.getBytes(field));
          }
        }
      } catch (SQLException e) {
        logger.error("error getting server for keystore", e);
        Sentry.captureException(e);
        return null;
      }
    }

    @Override
    public String getKeyStorePassword() {
      return "whisper";
    }
  }
}
