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
import io.finn.signald.Config;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
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

public interface IServersTable {
  UUID DEFAULT_SERVER = UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID);

  String SERVER_UUID = "server_uuid";
  String SERVICE_URL = "service_url";
  String CDN_URLS = "cdn_urls";
  String CONTACT_DISCOVERY_URL = "contact_discovery_url";
  String KEY_BACKUP_URL = "key_backup_url";
  String STORAGE_URL = "storage_url";
  String ZK_GROUP_PUBLIC_PARAMS = "zk_group_public_params";
  String UNIDENTIFIED_SENDER_ROOT = "unidentified_sender_root";
  String PROXY = "proxy";
  String CA = "ca";
  String KEY_BACKUP_SERVICE_NAME = "key_backup_service_name";
  String KEY_BACKUP_SERVICE_ID = "key_backup_service_id";
  String KEY_BACKUP_MRENCLAVE = "key_backup_mrenclave";
  String CDS_MRENCLAVE = "cds_mrenclave";
  String IAS_CA = "ias_ca";
  String CDSH_URL = "cdsh_url";

  Interceptor userAgentInterceptor = chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", BuildConfig.USER_AGENT).build());
  Logger logger = LogManager.getLogger();

  IServersTable.AbstractServer getDefaultServer() throws IOException, InvalidProxyException;
  AbstractServer getServer(UUID uuid) throws SQLException, IOException, ServerNotFoundException, InvalidProxyException;
  List<AbstractServer> allServers() throws SQLException;
  void create(AbstractServer server) throws SQLException, JsonProcessingException;
  void delete(UUID server)throws SQLException;

  abstract class AbstractServer {
    public UUID uuid;
    public String serviceURL;
    public Map<Integer, String> cdnURLs;
    public String contactDiscoveryURL;
    public String keyBackupURL;
    public String storageURL;
    public byte[] zkParams;

    public byte[] unidentifiedSenderRoot;
    public String proxy;
    public byte[] ca;

    public String keyBackupServiceName;
    public byte[] keyBackupServiceId;
    public String keyBackupMrenclave;
    public String cdsMrenclave;
    public byte[] iasCa;

    public String cdshURL;

    public abstract TrustStore GetTrustStore(UUID uuid, String field);

    public AbstractServer(UUID uuid, String serviceURL, Map<Integer, String> cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParams,
                          byte[] unidentifiedSenderRoot, String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave,
                          String cdsMrenclave, byte[] iasCa, String cdshURL) throws InvalidProxyException {
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
    public AbstractServer(UUID uuid, String serviceURL, String cdnURLs, String contactDiscoveryURL, String keyBackupURL, String storageURL, byte[] zkParam,
                          byte[] unidentifiedSenderRoot, String proxy, byte[] ca, String keyBackupServiceName, byte[] keyBackupServiceId, String keyBackupMrenclave,
                          String cdsMrenclave, byte[] cdsCa, String cdshURL) throws IOException, InvalidProxyException {
      this(uuid, serviceURL, (HashMap<Integer, String>)null, contactDiscoveryURL, keyBackupURL, storageURL, zkParam, unidentifiedSenderRoot, proxy, ca, keyBackupServiceName,
           keyBackupServiceId, keyBackupMrenclave, cdsMrenclave, cdsCa, cdshURL);
      var cdnURLType = new TypeReference<HashMap<Integer, String>>() {};
      this.cdnURLs = JSONUtil.GetMapper().readValue(cdnURLs, cdnURLType);
    }

    public SignalServiceConfiguration getSignalServiceConfiguration() {
      TrustStore trustStore = GetTrustStore(uuid, CA);

      Map<Integer, SignalCdnUrl[]> signalCdnUrlMap = new HashMap<>();
      for (HashMap.Entry<Integer, String> cdn : cdnURLs.entrySet()) {
        signalCdnUrlMap.put(cdn.getKey(), new SignalCdnUrl[] {new SignalCdnUrl(cdn.getValue(), trustStore)});
      }

      org.whispersystems.libsignal.util.guava.Optional<SignalProxy> proxyOptional = org.whispersystems.libsignal.util.guava.Optional.absent();
      if (proxy != null) {
        String[] parts = proxy.split(":");
        if (parts.length == 2) {
          int port = Integer.parseInt(parts[1]);
          SignalProxy proxy = new SignalProxy(parts[0], port);
          proxyOptional = org.whispersystems.libsignal.util.guava.Optional.of(proxy);
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
      if (Config.getLogHttpRequests()) {
        HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor();
        httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        interceptors.add(httpLoggingInterceptor);
      }
      return interceptors;
    }

    public KeyStore getIASKeyStore() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
      TrustStore trustStore = GetTrustStore(uuid, IAS_CA);
      KeyStore keyStore = KeyStore.getInstance("BKS");
      keyStore.load(trustStore.getKeyStoreInputStream(), trustStore.getKeyStorePassword().toCharArray());
      return keyStore;
    }

    public UUID getUuid() { return uuid; }

    public void setUuid(UUID uuid) { this.uuid = uuid; }

    public String getServiceURL() { return serviceURL; }

    public void setServiceURL(String serviceURL) { this.serviceURL = serviceURL; }

    public Map<Integer, String> getCdnURLs() { return cdnURLs; }

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
}
