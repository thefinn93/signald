/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.LinkingURI;
import io.finn.signald.db.AccountDataTable;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.ServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.UserAlreadyExistsException;
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
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.util.Base64;

public class ProvisioningManager {
  private final static ConcurrentHashMap<String, ProvisioningManager> provisioningManagers = new ConcurrentHashMap<>();

  private final SignalServiceAccountManager accountManager;
  private final IdentityKeyPair identityKey;
  private final int registrationId;
  private final String password;
  private final UUID server;

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
    return new URI("tsdevice:/?uuid=" + URLEncoder.encode(deviceUuid, "utf-8") + "&pub_key=" + URLEncoder.encode(deviceKey, "utf-8"));
  }

  public UUID finishDeviceLink(String deviceName, boolean overwrite) throws IOException, TimeoutException, UserAlreadyExistsException, InvalidInputException, SQLException,
                                                                            InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException {
    SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.getNewDeviceRegistration(identityKey);
    if (overwrite) {
      try {
        Manager.get(ret.getNumber()).deleteAccount(false);
      } catch (NoSuchAccountException | InvalidKeyException | ServerNotFoundException | InvalidProxyException ignored) {
      }
    }
    String encryptedDeviceName = DeviceNameUtil.encryptDeviceName(deviceName, ret.getIdentity().getPrivateKey());
    int deviceId = accountManager.finishNewDeviceRegistration(ret.getProvisioningCode(), false, true, registrationId, encryptedDeviceName);
    UUID uuid = ret.getUuid();
    if (AccountsTable.exists(uuid)) {
      throw new UserAlreadyExistsException(uuid);
    }

    Manager m = new Manager(ret.getUuid(), AccountData.createLinkedAccount(ret, password, registrationId, deviceId, server));
    AccountDataTable.set(m.getUUID(), AccountDataTable.Key.DEVICE_NAME, deviceName);

    m.refreshPreKeys();
    m.requestSyncGroups();
    m.requestSyncContacts();
    // m.requestSyncBlocked(); // TODO: implement support for blocking
    m.requestSyncConfiguration();
    return uuid;
  }
}
