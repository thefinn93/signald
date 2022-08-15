/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.db.*;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.UnidentifiedAccessUtil;
import io.reactivex.rxjava3.core.Single;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

public class RefreshProfileJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  public static final long PROFILE_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(4);
  public static final long MIN_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(30);

  private final Account account;
  private final Recipient recipient;

  public RefreshProfileJob(Account account, Recipient recipient) {
    this.account = account;
    this.recipient = recipient;
  }

  @Override
  public void run() throws NoSuchAccountException, SQLException, ServerNotFoundException, IOException, InvalidProxyException, InvalidKeyException {
    SignalServiceProfile.RequestType requestType = SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
    Database db = Database.Get(account.getACI());
    IProfilesTable.Profile profile = db.ProfilesTable.get(recipient);
    if (profile != null && System.currentTimeMillis() - profile.getLastUpdate() < MIN_REFRESH_INTERVAL) {
      logger.debug("refusing to refresh the same profile too frequently");
      return;
    }

    ProfileKey profileKey = db.ProfileKeysTable.getProfileKey(recipient);
    if (profileKey == null) {
      return;
    }
    SignalServiceAddress address = recipient.getAddress();
    Optional<UnidentifiedAccess> unidentifiedAccess = new UnidentifiedAccessUtil(account.getACI()).getUnidentifiedAccess();
    Locale locale = Locale.getDefault();
    ProfileService profileService = account.getSignalDependencies().getProfileService();
    ProfileAndCredential profileAndCredential;
    try {
      Single<ServiceResponse<ProfileAndCredential>> profileServiceResponse = profileService.getProfile(address, Optional.of(profileKey), unidentifiedAccess, requestType, locale);
      profileAndCredential = new ProfileService.ProfileResponseProcessor(profileServiceResponse.blockingGet()).getResultOrThrow();
    } catch (NonSuccessfulResponseCodeException e) {
      if (e instanceof RateLimitException) {
        logger.warn("rate limited trying to refresh profile");
      } else {
        logger.debug("error trying to refresh profile: {}", e.getMessage());
      }
      return;
    }

    Optional<ProfileKeyCredential> profileKeyCredential = profileAndCredential.getProfileKeyCredential();
    if (profileKeyCredential.isPresent()) {
      db.ProfileKeysTable.setProfileKeyCredential(recipient, profileKeyCredential.get());
    }

    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    SignalServiceProfile encryptedProfile = profileAndCredential.getProfile();

    try {
      String name = encryptedProfile.getName() == null ? "" : profileCipher.decryptString(Base64.decode(encryptedProfile.getName()));
      db.ProfilesTable.setSerializedName(recipient, name);
    } catch (InvalidCiphertextException e) {
      logger.debug("error decrypting profile name.", e);
    }

    try {
      String about = encryptedProfile.getAbout() == null ? null : profileCipher.decryptString(Base64.decode(encryptedProfile.getAbout()));
      db.ProfilesTable.setAbout(recipient, about);
    } catch (InvalidCiphertextException e) {
      logger.debug("error decrypting profile about text.", e);
    }

    try {
      String emoji = encryptedProfile.getAboutEmoji() == null ? null : profileCipher.decryptString(Base64.decode(encryptedProfile.getAboutEmoji()));
      db.ProfilesTable.setEmoji(recipient, emoji);
    } catch (InvalidCiphertextException e) {
      logger.debug("error decrypting profile emoji.", e);
    }

    try {
      String unidentifiedAccessString = null;
      if (encryptedProfile.getUnidentifiedAccess() != null && profileCipher.verifyUnidentifiedAccess(Base64.decode(encryptedProfile.getUnidentifiedAccess()))) {
        unidentifiedAccessString = encryptedProfile.getUnidentifiedAccess();
      }
      IProfileKeysTable.UnidentifiedAccessMode mode = getUnidentifiedAccessMode(unidentifiedAccessString, encryptedProfile.isUnrestrictedUnidentifiedAccess());
      db.ProfileKeysTable.setUnidentifiedAccessMode(recipient, mode);
    } catch (IOException ignored) {
    }

    byte[] encryptedPaymentsAddress = encryptedProfile.getPaymentAddress();
    if (encryptedPaymentsAddress != null) {
      try {
        byte[] decrypted = profileCipher.decryptWithLength(encryptedPaymentsAddress);
        db.ProfilesTable.setPaymentAddress(recipient, SignalServiceProtos.PaymentAddress.parseFrom(decrypted));
      } catch (InvalidCiphertextException ignored) {
      }
    } else {
      db.ProfilesTable.setPaymentAddress(recipient, null);
    }

    db.ProfilesTable.setBadges(recipient, encryptedProfile.getBadges());

    db.ProfileCapabilitiesTable.set(recipient, new IProfileCapabilitiesTable.Capabilities(encryptedProfile.getCapabilities()));
  }

  private IProfileKeysTable.UnidentifiedAccessMode getUnidentifiedAccessMode(String unidentifiedAccessVerifier, boolean unrestrictedUnidentifiedAccess) throws SQLException {
    Database db = Database.Get(account.getACI());
    IProfileKeysTable.UnidentifiedAccessMode currentMode = db.ProfileKeysTable.getUnidentifiedAccessMode(recipient);

    if (unrestrictedUnidentifiedAccess && unidentifiedAccessVerifier != null) {
      if (currentMode != IProfileKeysTable.UnidentifiedAccessMode.UNRESTRICTED) {
        logger.info("Marking recipient UD status as unrestricted.");
        return IProfileKeysTable.UnidentifiedAccessMode.UNRESTRICTED;
      }
    } else {
      ProfileKey profileKey = db.ProfileKeysTable.getProfileKey(recipient);
      if (profileKey == null || unidentifiedAccessVerifier == null) {
        if (currentMode != IProfileKeysTable.UnidentifiedAccessMode.DISABLED) {
          logger.info("Marking recipient UD status as disabled.");
          return IProfileKeysTable.UnidentifiedAccessMode.DISABLED;
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

        IProfileKeysTable.UnidentifiedAccessMode newMode =
            verifiedUnidentifiedAccess ? IProfileKeysTable.UnidentifiedAccessMode.ENABLED : IProfileKeysTable.UnidentifiedAccessMode.DISABLED;

        if (currentMode != newMode) {
          logger.info("Marking recipient UD status as " + newMode.name() + " after verification.");
          return newMode;
        }
      }
    }
    return currentMode; // no change
  }

  public static void queueIfNeeded(Account account, Recipient recipient) throws SQLException {
    IProfilesTable.Profile profile = account.getDB().ProfilesTable.get(recipient);
    if (profile == null || System.currentTimeMillis() - profile.getLastUpdate() > PROFILE_REFRESH_INTERVAL) {
      BackgroundJobRunnerThread.queue(new RefreshProfileJob(account, recipient));
    }
  }
}
