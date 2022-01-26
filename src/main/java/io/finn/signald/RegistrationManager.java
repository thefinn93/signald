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
import io.finn.signald.storage.AccountData;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.KeyUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.asamk.signal.util.RandomUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;

public class RegistrationManager {
  private final static Logger logger = LogManager.getLogger();
  private static final ConcurrentHashMap<String, RegistrationManager> registrationManagers = new ConcurrentHashMap<>();

  private final SignalServiceAccountManager accountManager;
  private final AccountData accountData;
  private final String e164;

  public static RegistrationManager get(String e164, UUID server) throws IOException, SQLException, ServerNotFoundException, InvalidProxyException {
    String key = e164 + server.toString();
    if (registrationManagers.containsKey(key)) {
      return registrationManagers.get(key);
    }
    RegistrationManager m = new RegistrationManager(e164, server);
    registrationManagers.put(key, m);
    logger.info("Created a registration manager for " + Util.redact(e164));
    return m;
  }

  RegistrationManager(String e164, UUID serverUUID) throws SQLException, ServerNotFoundException, InvalidProxyException, IOException {
    this.e164 = e164;
    ServersTable.Server server = ServersTable.getServer(serverUUID);
    SignalServiceConfiguration serviceConfiguration = server.getSignalServiceConfiguration();
    accountData = new AccountData(e164);
    accountData.registered = false;

    String password = Util.getSecret(18);
    PendingAccountDataTable.set(e164, PendingAccountDataTable.Key.PASSWORD, password);

    DynamicCredentialsProvider credentialProvider = new DynamicCredentialsProvider(null, e164, password, SignalServiceAddress.DEFAULT_DEVICE_ID);
    GroupsV2Operations groupsV2Operations = GroupsUtil.GetGroupsV2Operations(serviceConfiguration);
    accountManager = new SignalServiceAccountManager(serviceConfiguration, credentialProvider, BuildConfig.USER_AGENT, groupsV2Operations, ServiceConfig.AUTOMATIC_NETWORK_RETRY);
  }

  public void register(boolean voiceVerification, Optional<String> captcha, UUID server) throws IOException, InvalidInputException, SQLException {
    PendingAccountDataTable.set(e164, PendingAccountDataTable.Key.LOCAL_REGISTRATION_ID, KeyHelper.generateRegistrationId(false));
    PendingAccountDataTable.set(e164, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR, KeyUtil.generateIdentityKeyPair().serialize());
    PendingAccountDataTable.set(e164, PendingAccountDataTable.Key.SERVER_UUID, server.toString());

    ServiceResponse<RequestVerificationCodeResponse> r;
    if (voiceVerification) {
      r = accountManager.requestVoiceVerificationCode(Locale.getDefault(), captcha, Optional.absent(), Optional.absent());
    } else {
      r = accountManager.requestSmsVerificationCode(false, captcha, Optional.absent(), Optional.absent());
    }
    handleResponseException(r);

    accountData.init();
    accountData.save();
  }

  public Manager verifyAccount(String verificationCode)
      throws IOException, InvalidInputException, SQLException, InvalidProxyException, InvalidKeyException, ServerNotFoundException, NoSuchAccountException {
    verificationCode = verificationCode.replace("-", "");
    int registrationID = PendingAccountDataTable.getInt(e164, PendingAccountDataTable.Key.LOCAL_REGISTRATION_ID);
    ProfileKey profileKey = generateProfileKey();
    byte[] unidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
    ServiceResponse<VerifyAccountResponse> r = accountManager.verifyAccount(verificationCode, registrationID, true, unidentifiedAccessKey, false, ServiceConfig.CAPABILITIES, true);
    handleResponseException(r);

    VerifyAccountResponse result = r.getResult().get();
    ACI aci = ACI.from(UUID.fromString(result.getUuid()));
    accountData.setUUID(aci);
    Account account = new Account(aci);

    String server = PendingAccountDataTable.getString(e164, PendingAccountDataTable.Key.SERVER_UUID);
    AccountsTable.add(e164, aci, getFileName(), server == null ? null : UUID.fromString(server));

    AccountDataTable.set(aci, AccountDataTable.Key.LAST_ACCOUNT_REPAIR, AccountRepair.ACCOUNT_REPAIR_VERSION_CLEAR_SENDER_KEY_SHARED);

    String password = PendingAccountDataTable.getString(e164, PendingAccountDataTable.Key.PASSWORD);
    account.setPassword(password);

    byte[] identityKeyPair = PendingAccountDataTable.getBytes(e164, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR);
    account.setIdentityKeyPair(new IdentityKeyPair(identityKeyPair));

    Recipient self = new RecipientsTable(aci).get(aci);
    new IdentityKeysTable(aci).saveIdentity(self, new IdentityKeyPair(identityKeyPair).getPublicKey(), TrustLevel.TRUSTED_VERIFIED);

    account.setLocalRegistrationId(registrationID);
    account.setDeviceId(SignalServiceAddress.DEFAULT_DEVICE_ID);

    PendingAccountDataTable.clear(e164);
    accountData.registered = true;
    accountData.init();
    accountData.setProfileKey(profileKey);
    accountData.save();

    return Manager.get(aci);
  }

  public String getE164() { return e164; }

  private String getFileName() { return Manager.getFileName(e164); }

  public boolean hasPendingKeys() throws SQLException { return PendingAccountDataTable.getBytes(e164, PendingAccountDataTable.Key.OWN_IDENTITY_KEY_PAIR) != null; }

  public boolean isRegistered() { return accountData.registered; }

  private void handleResponseException(final ServiceResponse<?> response) throws IOException {
    final Optional<Throwable> throwableOptional = response.getExecutionError().or(response.getApplicationError());
    if (throwableOptional.isPresent()) {
      if (throwableOptional.get() instanceof IOException) {
        throw(IOException) throwableOptional.get();
      } else {
        throw new IOException(throwableOptional.get());
      }
    }
  }

  private ProfileKey generateProfileKey() throws InvalidInputException {
    byte[] key = new byte[32];
    RandomUtils.getSecureRandom().nextBytes(key);
    return new ProfileKey(key);
  }
}
