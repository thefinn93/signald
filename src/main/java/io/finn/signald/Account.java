/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.db.*;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.util.Base64;

public class Account {
  private static final Logger logger = LogManager.getLogger();
  private final static int ACCOUNT_REFRESH_VERSION = 6;
  private final ACI aci;

  public Account(ACI aci) { this.aci = aci; }

  public String getE164() throws SQLException, NoSuchAccountException { return Database.Get().AccountsTable.getE164(aci); }

  public UUID getUUID() { return aci.uuid(); }

  public ACI getACI() { return aci; }

  public void setPNI(PNI pni) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.PNI, pni.toString()); }

  public void setPNI() throws NoSuchAccountException, SQLException, ServerNotFoundException, IOException, InvalidProxyException {
    logger.debug("asking server for our PNI");
    WhoAmIResponse whoami = getSignalDependencies().getAccountManager().getWhoAmI();
    if (whoami.getPni() != null) {
      logger.info("successfully got PNI from server");
      setPNI(PNI.parseOrThrow(whoami.getPni()));
    }
  }

  public PNI getPNI() throws SQLException { return PNI.parseOrNull(Database.Get().AccountDataTable.getString(aci, IAccountDataTable.Key.PNI)); }

  public SignalServiceConfiguration getServiceConfiguration() throws SQLException, ServerNotFoundException, InvalidProxyException, IOException {
    return Database.Get().AccountsTable.getServer(aci).getSignalServiceConfiguration();
  }

  public SignalDependencies getSignalDependencies() throws SQLException, ServerNotFoundException, IOException, InvalidProxyException, NoSuchAccountException {
    return SignalDependencies.get(aci);
  }

  public DatabaseAccountDataStore getProtocolStore() { return new DatabaseAccountDataStore(aci); }

  public DatabaseDataStore getDataStore() { return new DatabaseDataStore(aci); }

  public Groups getGroups() throws SQLException, ServerNotFoundException, NoSuchAccountException, InvalidProxyException, IOException { return new Groups(aci); }

  public DynamicCredentialsProvider getCredentialsProvider() throws SQLException, NoSuchAccountException {
    return new DynamicCredentialsProvider(aci, getPNI(), getE164(), getPassword(), getDeviceId());
  }

  public int getLocalRegistrationId() throws SQLException { return Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.LOCAL_REGISTRATION_ID); }

  public void setLocalRegistrationId(int localRegistrationId) throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LOCAL_REGISTRATION_ID, localRegistrationId);
  }

  public int getDeviceId() throws SQLException { return Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.DEVICE_ID); }

  public void setDeviceId(int deviceId) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.DEVICE_ID, deviceId); }

  public boolean getMultiDevice() {
    try {
      Boolean isMultidevice = Database.Get().AccountDataTable.getBoolean(aci, IAccountDataTable.Key.MULTI_DEVICE);
      if (isMultidevice == null) {
        isMultidevice = getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID;
        getDB().AccountDataTable.set(aci, IAccountDataTable.Key.MULTI_DEVICE, isMultidevice);
        return false;
      }
      return isMultidevice;
    } catch (SQLException e) {
      logger.error("error fetching multidevice status from db", e);
      Sentry.captureException(e);
      return false;
    }
  }

  public void setMultiDevice(boolean multidevice) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.MULTI_DEVICE, multidevice); }

  public String getPassword() throws SQLException { return Database.Get().AccountDataTable.getString(aci, IAccountDataTable.Key.PASSWORD); }

  public void setPassword(String password) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.PASSWORD, password); }

  public IdentityKeyPair getACIIdentityKeyPair() throws SQLException {
    byte[] serialized = Database.Get().AccountDataTable.getBytes(aci, IAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR);
    if (serialized == null) {
      return null;
    }
    return new IdentityKeyPair(serialized);
  }

  public IdentityKeyPair getPNIIdentityKeyPair() throws SQLException {
    byte[] serialized = Database.Get().AccountDataTable.getBytes(aci, IAccountDataTable.Key.PNI_IDENTITY_KEY_PAIR);
    if (serialized == null) {
      return null;
    }
    return new IdentityKeyPair(serialized);
  }

  public void setACIIdentityKeyPair(IdentityKeyPair identityKeyPair) throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR, identityKeyPair.serialize());
  }

  public void setPNIIdentityKeyPair(@Nullable IdentityKeyPair identityKeyPair) throws SQLException {
    if (identityKeyPair == null) {
      return;
    }
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.PNI_IDENTITY_KEY_PAIR, identityKeyPair.serialize());
  }

  public long getLastPreKeyRefresh() throws SQLException { return Database.Get().AccountDataTable.getLong(aci, IAccountDataTable.Key.LAST_PRE_KEY_REFRESH); }

  public void setLastPreKeyRefreshNow() throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LAST_PRE_KEY_REFRESH, System.currentTimeMillis()); }

  public void setLastPreKeyRefresh(long timestamp) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LAST_PRE_KEY_REFRESH, timestamp); }

  public String getDeviceName() throws SQLException { return Database.Get().AccountDataTable.getString(aci, IAccountDataTable.Key.DEVICE_NAME); }

  public void setDeviceName(String deviceName) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.DEVICE_NAME, deviceName); }

  public int getLastAccountRefresh() throws SQLException { return Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.LAST_ACCOUNT_REFRESH); }

  public void setLastAccountRefresh(int accountRefreshVersion) throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LAST_ACCOUNT_REFRESH, accountRefreshVersion);
  }

  public byte[] getSenderCertificate() throws SQLException { return Database.Get().AccountDataTable.getBytes(aci, IAccountDataTable.Key.SENDER_CERTIFICATE); }

  public void setSenderCertificate(byte[] senderCertificate) throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.SENDER_CERTIFICATE, senderCertificate);
  }

  public long getLastSenderCertificateRefreshTime() throws SQLException {
    return Database.Get().AccountDataTable.getLong(aci, IAccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME);
  }

  public void setSenderCertificateRefreshTimeNow() throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME, System.currentTimeMillis());
  }

  public int getPreKeyIdOffset() throws SQLException {
    int offset = Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.PRE_KEY_ID_OFFSET);
    if (offset == -1) {
      return 0;
    }
    return offset;
  }

  public void setPreKeyIdOffset(int preKeyIdOffset) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.PRE_KEY_ID_OFFSET, preKeyIdOffset); }

  public int getNextSignedPreKeyId() throws SQLException {
    int id = Database.Get().AccountDataTable.getInt(aci, IAccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID);
    if (id == -1) {
      return 0;
    }
    return id;
  }

  public void setNextSignedPreKeyId(int nextSignedPreKeyId) throws SQLException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID, nextSignedPreKeyId);
  }

  public Recipient getSelf() throws SQLException, IOException { return getDB().RecipientsTable.get(aci); }

  public void setStorageKey(StorageKey storageKey) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.STORAGE_KEY, storageKey.serialize()); }

  public StorageKey getStorageKey() throws SQLException {
    byte[] bytes = Database.Get().AccountDataTable.getBytes(aci, IAccountDataTable.Key.STORAGE_KEY);
    if (bytes == null) {
      return null;
    }
    return new StorageKey(bytes);
  }

  public void setStorageManifestVersion(long version) throws SQLException { Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.STORAGE_MANIFEST_VERSION, version); }

  public long getStorageManifestVersion() throws SQLException { return Database.Get().AccountDataTable.getLong(aci, IAccountDataTable.Key.STORAGE_MANIFEST_VERSION); }

  public Database getDB() { return Database.Get(aci); }

  public boolean exists() throws SQLException { return Database.Get().AccountsTable.exists(aci); }

  public void delete(boolean remote)throws IOException, SQLException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.PENDING_DELETION, true);

    if (remote) {
      getSignalDependencies().getAccountManager().deleteAccount();
    }

    SignalDependencies.delete(aci);

    try {
      Manager.get(aci).deleteAccount();
    } catch (InvalidKeyException e) {
      logger.error("unexpected error while deleting account: ", e);
    }

    logger.debug("deleting account from database");
    Database.DeleteAccount(aci, getE164());
  }

  public void refreshIfNeeded() throws SQLException, NoSuchAccountException, ServerNotFoundException, IOException, InvalidProxyException {
    if (getLastAccountRefresh() < ACCOUNT_REFRESH_VERSION) {
      refresh();
    }
  }

  public void refresh() throws SQLException, NoSuchAccountException, ServerNotFoundException, IOException, InvalidProxyException {
    String deviceName = getDeviceName();
    if (deviceName == null) {
      deviceName = "signald";
      setDeviceName(deviceName);
    }
    deviceName = DeviceNameUtil.encryptDeviceName(deviceName, getProtocolStore().getIdentityKeyPair().getPrivateKey());
    ProfileKey ownProfileKey = getDB().ProfileKeysTable.getProfileKey(getSelf());
    byte[] ownUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(ownProfileKey);
    int localRegistrationId = getLocalRegistrationId();
    getSignalDependencies().getAccountManager().setAccountAttributes(null, localRegistrationId, true, null, null, ownUnidentifiedAccessKey, true, ServiceConfig.CAPABILITIES, true,
                                                                     Base64.decode(deviceName));
    setLastAccountRefresh(ACCOUNT_REFRESH_VERSION);
  }
}
