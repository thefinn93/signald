/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald;

import io.finn.signald.db.AccountDataTable;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.DatabaseProtocolStore;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public class Account {
  private static final Logger logger = LogManager.getLogger();
  private final UUID accountUUID;

  public Account(UUID accountUUID) { this.accountUUID = accountUUID; }

  public String getE164() throws SQLException, NoSuchAccountException { return AccountsTable.getE164(accountUUID); }

  public UUID getUUID() { return accountUUID; }

  public RecipientsTable getRecipients() { return new RecipientsTable(accountUUID); }

  public SignalDependencies getSignalDependencies() throws SQLException, ServerNotFoundException, IOException, InvalidProxyException, NoSuchAccountException {
    return SignalDependencies.get(accountUUID);
  }

  public DatabaseProtocolStore getProtocolStore() { return new DatabaseProtocolStore(accountUUID); }

  public DynamicCredentialsProvider getCredentialsProvider() throws SQLException, NoSuchAccountException {
    return new DynamicCredentialsProvider(accountUUID, getE164(), getPassword(), getDeviceId());
  }

  public int getLocalRegistrationId() throws SQLException { return AccountDataTable.getInt(accountUUID, AccountDataTable.Key.LOCAL_REGISTRATION_ID); }

  public void setLocalRegistrationId(int localRegistrationId) throws SQLException {
    AccountDataTable.set(accountUUID, AccountDataTable.Key.LOCAL_REGISTRATION_ID, localRegistrationId);
  }

  public int getDeviceId() throws SQLException { return AccountDataTable.getInt(accountUUID, AccountDataTable.Key.DEVICE_ID); }

  public void setDeviceId(int deviceId) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.DEVICE_ID, deviceId); }

  public boolean getMultiDevice() {
    try {
      Boolean isMultidevice = AccountDataTable.getBoolean(accountUUID, AccountDataTable.Key.MULTI_DEVICE);
      if (isMultidevice == null) {
        isMultidevice = getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID;
        AccountDataTable.set(accountUUID, AccountDataTable.Key.MULTI_DEVICE, isMultidevice);
        return false;
      }
      return isMultidevice;
    } catch (SQLException e) {
      logger.error("error fetching mutlidevice status from db", e);
      return false;
    }
  }

  public void setMultiDevice(boolean multidevice) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.MULTI_DEVICE, multidevice); }

  public String getPassword() throws SQLException { return AccountDataTable.getString(accountUUID, AccountDataTable.Key.PASSWORD); }

  public void setPassword(String password) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.PASSWORD, password); }

  public IdentityKeyPair getIdentityKeyPair() throws SQLException {
    byte[] serialized = AccountDataTable.getBytes(accountUUID, AccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
    if (serialized == null) {
      return null;
    }
    return new IdentityKeyPair(serialized);
  }

  public void setIdentityKeyPair(IdentityKeyPair identityKeyPair) throws SQLException {
    AccountDataTable.set(accountUUID, AccountDataTable.Key.OWN_IDENTITY_KEY_PAIR, identityKeyPair.serialize());
  }

  public long getLastPreKeyRefresh() throws SQLException { return AccountDataTable.getLong(accountUUID, AccountDataTable.Key.LAST_PRE_KEY_REFRESH); }

  public void setLastPreKeyRefreshNow() throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.LAST_PRE_KEY_REFRESH, System.currentTimeMillis()); }

  public void setLastPreKeyRefresh(long timestamp) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.LAST_PRE_KEY_REFRESH, timestamp); }

  public String getDeviceName() throws SQLException { return AccountDataTable.getString(accountUUID, AccountDataTable.Key.DEVICE_NAME); }

  public void setDeviceName(String deviceName) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.DEVICE_NAME, deviceName); }

  public int getLastAccountRefresh() throws SQLException { return AccountDataTable.getInt(accountUUID, AccountDataTable.Key.LAST_ACCOUNT_REFRESH); }

  public void setLastAccountRefresh(int accountRefreshVersion) throws SQLException {
    AccountDataTable.set(accountUUID, AccountDataTable.Key.LAST_ACCOUNT_REFRESH, accountRefreshVersion);
  }

  public byte[] getSenderCertificate() throws SQLException { return AccountDataTable.getBytes(accountUUID, AccountDataTable.Key.SENDER_CERTIFICATE); }

  public void setSenderCertificate(byte[] senderCertificate) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.SENDER_CERTIFICATE, senderCertificate); }

  public long getLastSenderCertificateRefreshTime() throws SQLException { return AccountDataTable.getLong(accountUUID, AccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME); }

  public void setSenderCertificateRefreshTimeNow() throws SQLException {
    AccountDataTable.set(accountUUID, AccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME, System.currentTimeMillis());
  }

  public int getPreKeyIdOffset() throws SQLException { return AccountDataTable.getInt(accountUUID, AccountDataTable.Key.PRE_KEY_ID_OFFSET); }

  public void setPreKeyIdOffset(int preKeyIdOffset) throws SQLException { AccountDataTable.set(accountUUID, AccountDataTable.Key.PRE_KEY_ID_OFFSET, preKeyIdOffset); }

  public int getNextSignedPreKeyId() throws SQLException { return AccountDataTable.getInt(accountUUID, AccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID); }

  public void setNextSignedPreKeyId(int nextSignedPreKeyId) throws SQLException {
    AccountDataTable.set(accountUUID, AccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID, nextSignedPreKeyId);
  }
}
