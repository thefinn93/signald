/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.LinkingURI;
import io.finn.signald.db.AccountDataTable;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.ServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.UserAlreadyExistsException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.SendSyncRequestJob;
import io.finn.signald.storage.AccountData;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.KeyUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.util.Base64;

public class ProvisioningManager {
  private final static ConcurrentHashMap<String, ProvisioningManager> provisioningManagers = new ConcurrentHashMap<>();

  private final SignalServiceAccountManager accountManager;
  private final IdentityKeyPair identityKey;
  private final int registrationId;
  private final String password;
  private final UUID server;
  private SignalServiceAccountManager.NewDeviceRegistrationReturn newDeviceRegistration;

  public static LinkingURI create(UUID server) throws TimeoutException, IOException, URISyntaxException, SQLException, ServerNotFoundException, InvalidProxyException {
    UUID sessionID = UUID.randomUUID();
    ProvisioningManager pm = new ProvisioningManager(server);
    provisioningManagers.put(sessionID.toString(), pm);
    return new LinkingURI(sessionID.toString(), pm);
  }

  public static ProvisioningManager get(String sessionID) { return provisioningManagers.get(sessionID); }

  public ProvisioningManager(UUID server) throws IOException, SQLException, ServerNotFoundException, InvalidProxyException {
    this.server = server;
    identityKey = KeyUtil.generateIdentityKeyPair();
    registrationId = KeyHelper.generateRegistrationId(false);
    password = Util.getSecret(18);
    final SleepTimer timer = new UptimeSleepTimer();
    DynamicCredentialsProvider credentialProvider = new DynamicCredentialsProvider(null, null, password, SignalServiceAddress.DEFAULT_DEVICE_ID);
    SignalServiceConfiguration serviceConfiguration = ServersTable.getServer(server).getSignalServiceConfiguration();
    accountManager = new SignalServiceAccountManager(serviceConfiguration, credentialProvider, BuildConfig.SIGNAL_AGENT, GroupsUtil.GetGroupsV2Operations(serviceConfiguration),
                                                     ServiceConfig.AUTOMATIC_NETWORK_RETRY);
  }

  public URI getDeviceLinkUri() throws TimeoutException, IOException, URISyntaxException {
    String deviceUuid = accountManager.getNewDeviceUuid();
    String deviceKey = Base64.encodeBytesWithoutPadding(identityKey.getPublicKey().getPublicKey().serialize());
    return new URI("sgnl://linkdevice?uuid=" + URLEncoder.encode(deviceUuid, "utf-8") + "&pub_key=" + URLEncoder.encode(deviceKey, "utf-8"));
  }

  public void waitForScan() throws IOException, TimeoutException { newDeviceRegistration = accountManager.getNewDeviceRegistration(identityKey); }

  public ACI finishDeviceLink(String deviceName, boolean overwrite) throws IOException, TimeoutException, UserAlreadyExistsException, InvalidInputException, SQLException,
                                                                           InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException,
                                                                           NoSuchAccountException {
    if (newDeviceRegistration == null) {
      waitForScan();
    }

    if (overwrite) {
      try {
        Manager.get(newDeviceRegistration.getNumber()).deleteAccount(false);
      } catch (NoSuchAccountException | InvalidKeyException | ServerNotFoundException | InvalidProxyException ignored) {
      }
    }
    String encryptedDeviceName = DeviceNameUtil.encryptDeviceName(deviceName, newDeviceRegistration.getIdentity().getPrivateKey());
    int deviceId = accountManager.finishNewDeviceRegistration(newDeviceRegistration.getProvisioningCode(), false, true, registrationId, encryptedDeviceName);

    ACI aci = newDeviceRegistration.getAci();
    if (AccountsTable.exists(aci)) {
      throw new UserAlreadyExistsException(aci);
    }

    AccountDataTable.set(aci, AccountDataTable.Key.LAST_ACCOUNT_REPAIR, AccountRepair.ACCOUNT_REPAIR_VERSION_CLEAR_SENDER_KEY_SHARED);

    Manager m = new Manager(newDeviceRegistration.getAci(), AccountData.createLinkedAccount(newDeviceRegistration, password, registrationId, deviceId, server));
    Account account = m.getAccount();
    account.setDeviceName(deviceName);

    m.refreshPreKeys();
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.GROUPS));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.CONTACTS));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.BLOCKED));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.KEYS));

    return aci;
  }
}
