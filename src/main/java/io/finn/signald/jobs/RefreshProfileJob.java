/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.MessageReceiver;
import io.finn.signald.SignalDependencies;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.storage.SignalProfile;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.util.Base64;

public class RefreshProfileJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  public static final long PROFILE_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(1);

  public ProfileAndCredentialEntry profileEntry;
  public Manager m;

  public RefreshProfileJob(Manager manager, ProfileAndCredentialEntry p) {
    profileEntry = p;
    m = manager;
  }

  public boolean needsRefresh() {
    return profileEntry.getProfileKeyCredential() == null || System.currentTimeMillis() - profileEntry.getLastUpdateTimestamp() > PROFILE_REFRESH_INTERVAL;
  }

  @Override
  public void run()
      throws IOException, ExecutionException, InterruptedException, TimeoutException, SQLException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    AccountData accountData = m.getAccountData();
    if (profileEntry == null) {
      logger.debug("refresh job scheduled for address with no stored profile key. skipping");
      return;
    }

    if (!needsRefresh()) {
      return;
    }

    SignalServiceProfile.RequestType requestType = SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
    Optional<ProfileKey> profileKeyOptional = Optional.fromNullable(profileEntry.getProfileKey());
    SignalServiceAddress address = profileEntry.getServiceAddress();
    Optional<UnidentifiedAccess> unidentifiedAccess = m.getUnidentifiedAccess();
    Locale locale = Locale.getDefault();

    ProfileService profileService = SignalDependencies.get(m.getACI()).getProfileService();
    Single<ServiceResponse<ProfileAndCredential>> profileServiceResponse = profileService.getProfile(address, profileKeyOptional, unidentifiedAccess, requestType, locale);
    ProfileAndCredential profileAndCredential = new ProfileService.ProfileResponseProcessor(profileServiceResponse.blockingGet()).getResultOrThrow();

    long now = System.currentTimeMillis();
    ProfileKeyCredential profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
    Recipient recipient = m.getRecipientsTable().get(profileEntry.getServiceAddress());
    final SignalProfile profile = m.decryptProfile(recipient, profileEntry.getProfileKey(), profileAndCredential.getProfile());
    final ProfileAndCredentialEntry.UnidentifiedAccessMode unidentifiedAccessMode =
        getUnidentifiedAccessMode(profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    accountData.profileCredentialStore.update(profileEntry.getServiceAddress(), profileEntry.getProfileKey(), now, profile, profileKeyCredential, unidentifiedAccessMode);
    accountData.saveIfNeeded();
  }

  private ProfileAndCredentialEntry.UnidentifiedAccessMode getUnidentifiedAccessMode(String unidentifiedAccessVerifier, boolean unrestrictedUnidentifiedAccess) {
    ProfileAndCredentialEntry currentEntry = m.getAccountData().profileCredentialStore.get(profileEntry.getServiceAddress());
    ProfileKey profileKey = currentEntry.getProfileKey();

    if (unrestrictedUnidentifiedAccess && unidentifiedAccessVerifier != null) {
      if (currentEntry.getUnidentifiedAccessMode() != ProfileAndCredentialEntry.UnidentifiedAccessMode.UNRESTRICTED) {
        logger.info("Marking recipient UD status as unrestricted.");
        return ProfileAndCredentialEntry.UnidentifiedAccessMode.UNRESTRICTED;
      }
    } else if (profileKey == null || unidentifiedAccessVerifier == null) {
      if (currentEntry.getUnidentifiedAccessMode() != ProfileAndCredentialEntry.UnidentifiedAccessMode.DISABLED) {
        logger.info("Marking recipient UD status as disabled.");
        return ProfileAndCredentialEntry.UnidentifiedAccessMode.DISABLED;
      }
    } else {
      ProfileCipher profileCipher = new ProfileCipher(profileKey);
      boolean verifiedUnidentifiedAccess;

      try {
        verifiedUnidentifiedAccess = profileCipher.verifyUnidentifiedAccess(Base64.decode(unidentifiedAccessVerifier));
      } catch (IOException e) {
        logger.warn("error verifying unidentified access", e);
        verifiedUnidentifiedAccess = false;
      }

      ProfileAndCredentialEntry.UnidentifiedAccessMode mode =
          verifiedUnidentifiedAccess ? ProfileAndCredentialEntry.UnidentifiedAccessMode.ENABLED : ProfileAndCredentialEntry.UnidentifiedAccessMode.DISABLED;

      if (currentEntry.getUnidentifiedAccessMode() != mode) {
        logger.info("Marking recipient UD status as " + mode.name() + " after verification.");
        return mode;
      }
    }
    return currentEntry.getUnidentifiedAccessMode(); // no change
  }
}
