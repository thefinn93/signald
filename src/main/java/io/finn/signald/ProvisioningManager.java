/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.LinkingURI;
import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.UserAlreadyExistsException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshPreKeysJob;
import io.finn.signald.jobs.SendSyncRequestJob;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.KeyUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.util.Base64;

public class ProvisioningManager {
  private final static ConcurrentHashMap<String, ProvisioningManager> provisioningManagers = new ConcurrentHashMap<>();
  private final static Logger logger = LogManager.getLogger();

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
    DynamicCredentialsProvider credentialProvider = new DynamicCredentialsProvider(null, null, null, password, SignalServiceAddress.DEFAULT_DEVICE_ID);
    SignalServiceConfiguration serviceConfiguration = Database.Get().ServersTable.getServer(server).getSignalServiceConfiguration();
    accountManager = new SignalServiceAccountManager(serviceConfiguration, credentialProvider, BuildConfig.SIGNAL_AGENT, GroupsUtil.GetGroupsV2Operations(serviceConfiguration),
                                                     ServiceConfig.AUTOMATIC_NETWORK_RETRY);
  }

  public URI getDeviceLinkUri() throws TimeoutException, IOException, URISyntaxException {
    String deviceUuid = accountManager.getNewDeviceUuid();
    String deviceKey = Base64.encodeBytesWithoutPadding(identityKey.getPublicKey().getPublicKey().serialize());
    return new URI("sgnl://linkdevice?uuid=" + URLEncoder.encode(deviceUuid, StandardCharsets.UTF_8) + "&pub_key=" + URLEncoder.encode(deviceKey, StandardCharsets.UTF_8));
  }

  public void waitForScan() throws IOException, TimeoutException { newDeviceRegistration = accountManager.getNewDeviceRegistration(identityKey); }

  public ACI finishDeviceLink(String deviceName, boolean overwrite) throws IOException, TimeoutException, UserAlreadyExistsException, InvalidInputException, SQLException,
                                                                           InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException,
                                                                           NoSuchAccountException {
    if (newDeviceRegistration == null) {
      waitForScan();
    }

    if (overwrite && Database.Get().AccountsTable.exists(newDeviceRegistration.getAci())) {
      logger.info("linking to a new account but we already have data for this account uuid locally. overwriting as requested");
      new Account(newDeviceRegistration.getAci()).delete(false);
    }

    String encryptedDeviceName = DeviceNameUtil.encryptDeviceName(deviceName, newDeviceRegistration.getAciIdentity().getPrivateKey());
    int deviceId = accountManager.finishNewDeviceRegistration(newDeviceRegistration.getProvisioningCode(), false, true, registrationId, encryptedDeviceName);

    ACI aci = newDeviceRegistration.getAci();
    if (Database.Get().AccountsTable.exists(aci)) {
      throw new UserAlreadyExistsException(aci);
    }

    Database.Get().AccountsTable.add(newDeviceRegistration.getNumber(), newDeviceRegistration.getAci(), server);

    Account account = new Account(aci);
    account.setDeviceName(deviceName);
    if (newDeviceRegistration.getPni() != null) {
      account.setPNI(newDeviceRegistration.getPni());
    }
    account.setDeviceId(deviceId);
    account.setPassword(password);
    account.setACIIdentityKeyPair(newDeviceRegistration.getAciIdentity());
    account.setPNIIdentityKeyPair(newDeviceRegistration.getPniIdentity());
    account.setLocalRegistrationId(registrationId);

    // store all known identifiers in the recipients table
    account.getDB().RecipientsTable.get(newDeviceRegistration.getNumber(), newDeviceRegistration.getAci());

    if (newDeviceRegistration.getProfileKey() != null) {
      account.getDB().ProfileKeysTable.setProfileKey(account.getSelf(), newDeviceRegistration.getProfileKey());
    }

    Manager m = new Manager(newDeviceRegistration.getAci());

    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LAST_ACCOUNT_REPAIR, AccountRepair.getLatestVersion());

    try {
      new RefreshPreKeysJob(account).run();
    } catch (AuthorizationFailedException e) {
      logger.error("error setting up new account. See https://gitlab.com/signald/signald/-/issues/336");
      logger.info("some things that might be useful: overwrite={} database={} password matches={} aci={} pni={}", overwrite, Database.GetConnectionType().name(),
                  account.getPassword().equals(password), Util.redact(newDeviceRegistration.getAci()), Util.redact(newDeviceRegistration.getPni()));
      throw e;
    }
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.GROUPS));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.CONTACTS));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.BLOCKED));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.KEYS));
    BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.PNI_IDENTITY));

    return aci;
  }
}
