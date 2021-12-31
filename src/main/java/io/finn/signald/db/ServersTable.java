/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.finn.signald.BuildConfig;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.JSONUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import okhttp3.Interceptor;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.internal.configuration.*;
import org.whispersystems.util.Base64;

public class ServersTable {
  public static final UUID DEFAULT_SERVER = UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID);

  private static final String TABLE_NAME = "servers";
  private static final String SERVER_UUID = "server_uuid";
  private static final String SERVICE_URL = "service_url";
  private static final String CDN_URLS = "cdn_urls";
  private static final String CONTACT_DISCOVERY_URL = "contact_discovery_url";
  private static final String KEY_BACKUP_URL = "key_backup_url";
  private static final String STORAGE_URL = "storage_url";
  private static final String ZK_GROUP_PUBLIC_PARAMS = "zk_group_public_params";
  private static final String UNIDENTIFIED_SENDER_ROOT = "unidentified_sender_root";
  private static final String PROXY = "proxy";
  private static final String CA = "ca";
  private static final String KEY_BACKUP_SERVICE_NAME = "key_backup_service_name";
  private static final String KEY_BACKUP_SERVICE_ID = "key_backup_service_id";
  private static final String KEY_BACKUP_MRENCLAVE = "key_backup_mrenclave";
  private static final String CDS_MRENCLAVE = "cds_mrenclave";
  private static final String IAS_CA = "ias_ca";
  private static final String CDSH_URL = "cdsh_url";

  private static final Interceptor userAgentInterceptor = chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build());
  private static final Logger logger = LogManager.getLogger();
  private static boolean logHttpRequests = false;

  public static void setLogHttpRequests(boolean log) { logHttpRequests = log; }

  public static Server getDefaultServer() throws IOException, InvalidProxyException {
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

  public static Server getServer(UUID uuid) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + SERVER_UUID + "," + SERVICE_URL + ", " + CDN_URLS + "," + CONTACT_DISCOVERY_URL + ", " +
                                                                      KEY_BACKUP_URL + ", " + STORAGE_URL + ", " + ZK_GROUP_PUBLIC_PARAMS + ", " + UNIDENTIFIED_SENDER_ROOT + "," +
                                                                      PROXY + "," + CA + "," + KEY_BACKUP_SERVICE_NAME + "," + KEY_BACKUP_SERVICE_ID + "," + KEY_BACKUP_MRENCLAVE +
                                                                      "," + CDS_MRENCLAVE + "," + IAS_CA + " FROM " + TABLE_NAME + " WHERE " + SERVER_UUID + " = ?");
    statement.setString(1, uuid.toString());
    ResultSet rows = statement.executeQuery();

    // If there are no rows returned, check if it's one of the preloaded servers
    if (!rows.next()) {
      rows.close();

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
    rows.close();

    return new Server(serverUUID, serviceURL, cdnURLs, contactDiscoveryURL, keyBackupURL, storageURL, zkGroupPublicParams, unidentifiedSenderRoot, proxyString, ca,
                      keyBackupServiceName, keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, "");
  }

  public static List<Server> allServers() throws SQLException {
    List<Server> servers = new ArrayList<>();

    PreparedStatement statement =
        Database.getConn().prepareStatement("SELECT " + SERVER_UUID + "," + SERVICE_URL + ", " + CDN_URLS + "," + CONTACT_DISCOVERY_URL + ", " + KEY_BACKUP_URL + ", " +
                                            STORAGE_URL + ", " + ZK_GROUP_PUBLIC_PARAMS + ", " + UNIDENTIFIED_SENDER_ROOT + "," + PROXY + "," + CA + "," + KEY_BACKUP_SERVICE_NAME +
                                            "," + KEY_BACKUP_SERVICE_ID + "," + KEY_BACKUP_MRENCLAVE + "," + CDS_MRENCLAVE + "," + IAS_CA + " FROM " + TABLE_NAME);
    ResultSet rows = statement.executeQuery();
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
        logger.warn("failed to load signal server " + serverUUID.toString() + " from database: " + e.getMessage());
      }
    }
    rows.close();
    return servers;
  }

  public static void create(Server server) throws SQLException, JsonProcessingException {
    PreparedStatement statement = Database.getConn().prepareStatement("INSERT INTO " + TABLE_NAME + "(" + SERVER_UUID + "," + SERVICE_URL + "," + CDN_URLS + "," +
                                                                      CONTACT_DISCOVERY_URL + "," + KEY_BACKUP_URL + "," + STORAGE_URL + "," + ZK_GROUP_PUBLIC_PARAMS + "," +
                                                                      UNIDENTIFIED_SENDER_ROOT + "," + PROXY + "," + CA + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

    if (server.uuid == null) {
      server.uuid = UUID.randomUUID();
    }

    int i = 1;
    statement.setString(i++, server.uuid.toString());
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
    statement.executeUpdate();
  }

  public static void delete(UUID server)throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + SERVER_UUID + " = ?");
    statement.setString(1, server.toString());
    statement.executeUpdate();
  }

  public static class Server {
    UUID uuid;
    String serviceURL;
    HashMap<Integer, String> cdnURLs;
    String contactDiscoveryURL;
    String keyBackupURL;
    String storageURL;
    byte[] zkParams;

    byte[] unidentifiedSenderRoot;
    String proxy;
    byte[] ca;

    String keyBackupServiceName;
    byte[] keyBackupServiceId;
    String keyBackupMrenclave;
    String cdsMrenclave;
    byte[] iasCa;

    String cdshURL;

    public Server(UUID uuid, String serviceURL, HashMap<Integer, String> cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParams,
                  byte[] unidentifiedSenderRoot, String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave, String cdsMrenclave,
                  byte[] iasCa, String cdshURL) throws InvalidProxyException {
      this.uuid = uuid;
      this.serviceURL = serviceURL;
      this.cdnURLs = cdnURLs;
      this.contactDiscoveryURL = contactDiscoveryURL;
      this.keyBackupURL = keyBackupURL;
      this.storageURL = storageURL;
      this.zkParams = zkParams;
      this.unidentifiedSenderRoot = unidentifiedSenderRoot;

      if (proxy != null && !proxy.equals("")) {
        String[] parts = proxy.split(":");
        if (parts.length != 2 || Integer.parseInt(parts[1]) < 65535) {
          throw new InvalidProxyException(proxy);
        }
        this.proxy = proxy;
      }

      this.ca = ca;
      this.keyBackupServiceName = keyBackupServiceName;
      this.keyBackupServiceId = keyBackupServiceId;
      this.keyBackupMrenclave = keyBackupMrenclave;
      this.cdsMrenclave = cdsMrenclave;
      this.iasCa = iasCa;
      this.cdshURL = cdshURL;
    }

    // constructor that takes cdnURLs as a JSON-encoded string
    Server(UUID uuid, String serviceURL, String cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParam, byte[] unidentifiedSenderRoot,
           String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave, String cdsMrenclave, byte[] cdsCa, String cdshURL)
        throws IOException, InvalidProxyException {
      this(uuid, serviceURL, (HashMap<Integer, String>)null, contactDiscoveryURL, keyBackupURL, storageURL, zkParam, unidentifiedSenderRoot, proxy, ca, keyBackupServiceName,
           keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, cdshURL);
      TypeReference<HashMap<Integer, String>> cdnURLType = new TypeReference<HashMap<Integer, String>>() {};
      this.cdnURLs = JSONUtil.GetMapper().readValue(cdnURLs, cdnURLType);
    }

    public SignalServiceConfiguration getSignalServiceConfiguration() {
      TrustStore trustStore = new DatabaseTrustStore(uuid, CA);

      Map<Integer, SignalCdnUrl[]> signalCdnUrlMap = new HashMap<>();
      for (HashMap.Entry<Integer, String> cdn : cdnURLs.entrySet()) {
        signalCdnUrlMap.put(cdn.getKey(), new SignalCdnUrl[] {new SignalCdnUrl(cdn.getValue(), trustStore)});
      }

      Optional<SignalProxy> proxyOptional = Optional.absent();
      if (proxy != null) {
        String[] parts = proxy.split(":");
        if (parts.length == 2) {
          int port = Integer.parseInt(parts[1]);
          SignalProxy proxy = new SignalProxy(parts[0], port);
          proxyOptional = Optional.of(proxy);
        }
      }

      return new SignalServiceConfiguration(
          new SignalServiceUrl[] {new SignalServiceUrl(serviceURL, trustStore)},                            // SignalServiceUrl[] signalServiceUrls
          signalCdnUrlMap,                                                                                  // Map<Integer, SignalCdnUrl[]> signalCdnUrlMap
          new SignalContactDiscoveryUrl[] {new SignalContactDiscoveryUrl(contactDiscoveryURL, trustStore)}, // SignalContactDiscoveryUrl[] signalContactDiscoveryUrls
          new SignalKeyBackupServiceUrl[] {new SignalKeyBackupServiceUrl(keyBackupURL, trustStore)},        // SignalKeyBackupServiceUrl[] signalKeyBackupServiceUrls
          new SignalStorageUrl[] {new SignalStorageUrl(storageURL, trustStore)},                            // SignalStorageUrl[] signalStorageUrls
          new SignalCdshUrl[] {new SignalCdshUrl(cdshURL, trustStore)},                                     // SignalCdshUrl[] signalCdshUrls
          getInterceptors(),                                                                                // List<Interceptor> networkInterceptors
          Optional.absent(),                                                                                // Optional<Dns> dns
          proxyOptional,                                                                                    // Optional<SignalProxy> proxy
          zkParams                                                                                          // byte[] zkGroupServerPublicParams
      );
    }

    private List<Interceptor> getInterceptors() {
      List<Interceptor> interceptors = new ArrayList<>();
      interceptors.add(userAgentInterceptor);
      if (logHttpRequests) {
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        interceptors.add(httpLoggingInterceptor);
      }
      return interceptors;
    }

    public KeyStore getIASKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
      TrustStore trustStore = new DatabaseTrustStore(uuid, IAS_CA);
      KeyStore keyStore = KeyStore.getInstance("BKS");
      keyStore.load(trustStore.getKeyStoreInputStream(), trustStore.getKeyStorePassword().toCharArray());
      return keyStore;
    }

    public UUID getUuid() { return uuid; }

    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getServiceURL() { return serviceURL; }

    public void setServiceURL(String serviceURL) { this.serviceURL = serviceURL; }

    public HashMap<Integer, String> getCdnURLs() { return cdnURLs; }

    public void setCdnURLs(HashMap<Integer, String> cdnURLs) { this.cdnURLs = cdnURLs; }

    public String getContactDiscoveryURL() { return contactDiscoveryURL; }

    public void setContactDiscoveryURL(String contactDiscoveryURL) { this.contactDiscoveryURL = contactDiscoveryURL; }

    public String getKeyBackupURL() { return keyBackupURL; }

    public void setKeyBackupURL(String keyBackupURL) { this.keyBackupURL = keyBackupURL; }

    public String getStorageURL() { return storageURL; }

    public void setStorageURL(String storageURL) { this.storageURL = storageURL; }

    public byte[] getZkParams() { return zkParams; }

    public void setZkParams(byte[] zkParams) { this.zkParams = zkParams; }

    public ECPublicKey getUnidentifiedSenderRoot() throws InvalidKeyException { return Curve.decodePoint(unidentifiedSenderRoot, 0); }

    public void setUnidentifiedSenderRoot(byte[] unidentifiedSenderRoot) { this.unidentifiedSenderRoot = unidentifiedSenderRoot; }

    public String getProxy() { return proxy; }

    public void setProxy(String proxy) { this.proxy = proxy; }

    public byte[] getCa() { return ca; }

    public void setCa(byte[] ca) { this.ca = ca; }

    public String getKeyBackupServiceName() { return keyBackupServiceName; }

    public byte[] getKeyBackupServiceId() { return keyBackupServiceId; }

    public String getKeyBackupMrenclave() { return keyBackupMrenclave; }

    public String getCdsMrenclave() { return cdsMrenclave; }

    public byte[] getIasCa() { return iasCa; }
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
        PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + field + " FROM " + TABLE_NAME + " WHERE " + SERVER_UUID + " = ?");
        statement.setString(1, uuid.toString());
        ResultSet rows = statement.executeQuery();
        if (!rows.next()) {
          rows.close();
          logger.warn("this should never happen: no results for server when getting keystore");
          return null;
        }

        byte[] ca = rows.getBytes(field);
        return new ByteArrayInputStream(ca);
      } catch (SQLException e) {
        logger.error("error getting server for keystore", e);
        return null;
      }
    }

    @Override
    public String getKeyStorePassword() {
      return "whisper";
    }
  }
}
