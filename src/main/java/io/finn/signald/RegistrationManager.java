/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import io.finn.signald.db.IPendingAccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.KeyUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.asamk.signal.util.RandomUtils;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
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
  //  @Deprecated private final LegacyAccountData accountData;
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
    var server = Database.Get().ServersTable.getServer(serverUUID);
    SignalServiceConfiguration serviceConfiguration = server.getSignalServiceConfiguration();

    String password = Util.getSecret(18);
    Database.Get().PendingAccountDataTable.set(e164, IPendingAccountDataTable.Key.PASSWORD, password);

    DynamicCredentialsProvider credentialProvider = new DynamicCredentialsProvider(null, null, e164, password, SignalServiceAddress.DEFAULT_DEVICE_ID);
    GroupsV2Operations groupsV2Operations = GroupsUtil.GetGroupsV2Operations(serviceConfiguration);
    accountManager = new SignalServiceAccountManager(serviceConfiguration, credentialProvider, BuildConfig.USER_AGENT, groupsV2Operations, ServiceConfig.AUTOMATIC_NETWORK_RETRY);
  }

  public void register(boolean voiceVerification, Optional<String> captcha, UUID server) throws IOException, InvalidInputException, SQLException {
    Database.Get().PendingAccountDataTable.set(e164, IPendingAccountDataTable.Key.LOCAL_REGISTRATION_ID, KeyHelper.generateRegistrationId(false));
    Database.Get().PendingAccountDataTable.set(e164, IPendingAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR, KeyUtil.generateIdentityKeyPair().serialize());
    Database.Get().PendingAccountDataTable.set(e164, IPendingAccountDataTable.Key.PNI_IDENTITY_KEY_PAIR, KeyUtil.generateIdentityKeyPair().serialize());
    Database.Get().PendingAccountDataTable.set(e164, IPendingAccountDataTable.Key.SERVER_UUID, server.toString());

    ServiceResponse<RequestVerificationCodeResponse> r;
    if (voiceVerification) {
      r = accountManager.requestVoiceVerificationCode(Locale.getDefault(), captcha, Optional.empty(), Optional.empty());
    } else {
      r = accountManager.requestSmsVerificationCode(false, captcha, Optional.empty(), Optional.empty());
    }
    handleResponseException(r);
  }

  public Manager verifyAccount(String verificationCode)
      throws IOException, InvalidInputException, SQLException, InvalidProxyException, InvalidKeyException, ServerNotFoundException, NoSuchAccountException {
    verificationCode = verificationCode.replace("-", "");
    int registrationID = Database.Get().PendingAccountDataTable.getInt(e164, IPendingAccountDataTable.Key.LOCAL_REGISTRATION_ID);
    ProfileKey profileKey = generateProfileKey();
    byte[] unidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
    ServiceResponse<VerifyAccountResponse> r = accountManager.verifyAccount(verificationCode, registrationID, true, unidentifiedAccessKey, false, ServiceConfig.CAPABILITIES, true);
    handleResponseException(r);

    VerifyAccountResponse result = r.getResult().get();
    ACI aci = ACI.from(UUID.fromString(result.getUuid()));
    PNI pni = PNI.from(UUID.fromString(result.getPni()));
    Account account = new Account(aci);

    String server = Database.Get().PendingAccountDataTable.getString(e164, IPendingAccountDataTable.Key.SERVER_UUID);
    Database.Get().AccountsTable.add(e164, aci, server == null ? null : UUID.fromString(server));
    account.setPNI(pni);

    Database.Get().AccountDataTable.set(aci, IAccountDataTable.Key.LAST_ACCOUNT_REPAIR, AccountRepair.getLatestVersion());

    String password = Database.Get().PendingAccountDataTable.getString(e164, IPendingAccountDataTable.Key.PASSWORD);
    account.setPassword(password);

    IdentityKeyPair aciIdentityKeyPair = new IdentityKeyPair(Database.Get().PendingAccountDataTable.getBytes(e164, IPendingAccountDataTable.Key.PNI_IDENTITY_KEY_PAIR));
    account.setACIIdentityKeyPair(aciIdentityKeyPair);

    IdentityKeyPair pniIdentityKeyPair = new IdentityKeyPair(Database.Get().PendingAccountDataTable.getBytes(e164, IPendingAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR));
    account.setPNIIdentityKeyPair(pniIdentityKeyPair);

    account.getDB().IdentityKeysTable.saveIdentity(Database.Get(aci).RecipientsTable.get(aci), aciIdentityKeyPair.getPublicKey(), TrustLevel.TRUSTED_VERIFIED);

    account.setLocalRegistrationId(registrationID);
    account.setDeviceId(SignalServiceAddress.DEFAULT_DEVICE_ID);

    Database.Get().PendingAccountDataTable.clear(e164);

    account.getDB().ProfileKeysTable.setProfileKey(account.getSelf(), profileKey);

    account.getSignalDependencies().getAccountManager().setVersionedProfile(aci, profileKey, "", "", "", Optional.empty(), AvatarUploadParams.unchanged(false), List.of());

    return Manager.get(aci);
  }

  public String getE164() { return e164; }

  public boolean hasPendingKeys() throws SQLException {
    return Database.Get().PendingAccountDataTable.getBytes(e164, IPendingAccountDataTable.Key.ACI_IDENTITY_KEY_PAIR) != null &&
        Database.Get().PendingAccountDataTable.getBytes(e164, IPendingAccountDataTable.Key.PNI_IDENTITY_KEY_PAIR) != null;
  }

  public boolean isRegistered() {
    try {
      Database.Get().AccountsTable.getACI(e164);
      return true;
    } catch (NoSuchAccountException e) {
      return false;
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }

  private void handleResponseException(final ServiceResponse<?> response) throws IOException {
    final Optional<Throwable> throwableOptional = response.getExecutionError().or(response::getApplicationError);
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
