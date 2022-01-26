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
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public class Account {
  private static final Logger logger = LogManager.getLogger();
  private final ACI aci;

  public Account(UUID accountUUID) { this.aci = ACI.from(accountUUID); }

  public Account(ACI aci) { this.aci = aci; }

  public String getE164() throws SQLException, NoSuchAccountException { return AccountsTable.getE164(aci); }

  public UUID getUUID() { return aci.uuid(); }

  public ACI getACI() { return aci; }

  public SignalServiceConfiguration getServiceConfiguration() throws SQLException, ServerNotFoundException, InvalidProxyException, IOException {
    return AccountsTable.getServer(aci).getSignalServiceConfiguration();
  }

  public RecipientsTable getRecipients() { return new RecipientsTable(aci); }

  public SignalDependencies getSignalDependencies() throws SQLException, ServerNotFoundException, IOException, InvalidProxyException, NoSuchAccountException {
    return SignalDependencies.get(aci);
  }

  public DatabaseProtocolStore getProtocolStore() { return new DatabaseProtocolStore(aci); }

  public GroupsTable getGroupsTable() { return new GroupsTable(aci); }

  public Groups getGroups() throws SQLException, ServerNotFoundException, NoSuchAccountException, InvalidProxyException, IOException { return new Groups(aci); }

  public DynamicCredentialsProvider getCredentialsProvider() throws SQLException, NoSuchAccountException {
    return new DynamicCredentialsProvider(aci, getE164(), getPassword(), getDeviceId());
  }

  public int getLocalRegistrationId() throws SQLException { return AccountDataTable.getInt(aci, AccountDataTable.Key.LOCAL_REGISTRATION_ID); }

  public void setLocalRegistrationId(int localRegistrationId) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.LOCAL_REGISTRATION_ID, localRegistrationId); }

  public int getDeviceId() throws SQLException { return AccountDataTable.getInt(aci, AccountDataTable.Key.DEVICE_ID); }

  public void setDeviceId(int deviceId) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.DEVICE_ID, deviceId); }

  public boolean getMultiDevice() {
    try {
      Boolean isMultidevice = AccountDataTable.getBoolean(aci, AccountDataTable.Key.MULTI_DEVICE);
      if (isMultidevice == null) {
        isMultidevice = getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID;
        AccountDataTable.set(aci, AccountDataTable.Key.MULTI_DEVICE, isMultidevice);
        return false;
      }
      return isMultidevice;
    } catch (SQLException e) {
      logger.error("error fetching multidevice status from db", e);
      return false;
    }
  }

  public void setMultiDevice(boolean multidevice) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.MULTI_DEVICE, multidevice); }

  public String getPassword() throws SQLException { return AccountDataTable.getString(aci, AccountDataTable.Key.PASSWORD); }

  public void setPassword(String password) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.PASSWORD, password); }

  public IdentityKeyPair getIdentityKeyPair() throws SQLException {
    byte[] serialized = AccountDataTable.getBytes(aci, AccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
    if (serialized == null) {
      return null;
    }
    return new IdentityKeyPair(serialized);
  }

  public void setIdentityKeyPair(IdentityKeyPair identityKeyPair) throws SQLException {
    AccountDataTable.set(aci, AccountDataTable.Key.OWN_IDENTITY_KEY_PAIR, identityKeyPair.serialize());
  }

  public long getLastPreKeyRefresh() throws SQLException { return AccountDataTable.getLong(aci, AccountDataTable.Key.LAST_PRE_KEY_REFRESH); }

  public void setLastPreKeyRefreshNow() throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.LAST_PRE_KEY_REFRESH, System.currentTimeMillis()); }

  public void setLastPreKeyRefresh(long timestamp) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.LAST_PRE_KEY_REFRESH, timestamp); }

  public String getDeviceName() throws SQLException { return AccountDataTable.getString(aci, AccountDataTable.Key.DEVICE_NAME); }

  public void setDeviceName(String deviceName) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.DEVICE_NAME, deviceName); }

  public int getLastAccountRefresh() throws SQLException { return AccountDataTable.getInt(aci, AccountDataTable.Key.LAST_ACCOUNT_REFRESH); }

  public void setLastAccountRefresh(int accountRefreshVersion) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.LAST_ACCOUNT_REFRESH, accountRefreshVersion); }

  public byte[] getSenderCertificate() throws SQLException { return AccountDataTable.getBytes(aci, AccountDataTable.Key.SENDER_CERTIFICATE); }

  public void setSenderCertificate(byte[] senderCertificate) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.SENDER_CERTIFICATE, senderCertificate); }

  public long getLastSenderCertificateRefreshTime() throws SQLException { return AccountDataTable.getLong(aci, AccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME); }

  public void setSenderCertificateRefreshTimeNow() throws SQLException {
    AccountDataTable.set(aci, AccountDataTable.Key.SENDER_CERTIFICATE_REFRESH_TIME, System.currentTimeMillis());
  }

  public int getPreKeyIdOffset() throws SQLException {
    int offset = AccountDataTable.getInt(aci, AccountDataTable.Key.PRE_KEY_ID_OFFSET);
    if (offset == -1) {
      return 0;
    }
    return offset;
  }

  public void setPreKeyIdOffset(int preKeyIdOffset) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.PRE_KEY_ID_OFFSET, preKeyIdOffset); }

  public int getNextSignedPreKeyId() throws SQLException {
    int id = AccountDataTable.getInt(aci, AccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID);
    if (id == -1) {
      return 0;
    }
    return id;
  }

  public void setNextSignedPreKeyId(int nextSignedPreKeyId) throws SQLException { AccountDataTable.set(aci, AccountDataTable.Key.NEXT_SIGNED_PRE_KEY_ID, nextSignedPreKeyId); }

  public SenderKeySharedTable getSenderKeysSharedWith() { return new SenderKeySharedTable(aci); }

  public Recipient getSelf() throws SQLException, IOException { return getRecipients().get(aci); }
}
