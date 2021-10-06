/*
 * Copyright (C) 2021 Finn Herzfeld
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

package io.finn.signald.jobs;

import io.finn.signald.Manager;
import io.finn.signald.db.Recipient;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.storage.SignalProfile;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

public class RefreshProfileJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  public static final long PROFILE_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(12);

  public ProfileAndCredentialEntry profileEntry;
  public Manager m;

  // get ProfileAndCredentialEntry with accountData.profileCredentialStore.get(address);
  public RefreshProfileJob(Manager manager, ProfileAndCredentialEntry p) {
    profileEntry = p;
    m = manager;
  }

  public boolean needsRefresh() {
    return profileEntry.getProfileKeyCredential() == null || System.currentTimeMillis() - profileEntry.getLastUpdateTimestamp() > PROFILE_REFRESH_INTERVAL;
  }

  @Override
  public void run() throws InterruptedException, ExecutionException, TimeoutException, IOException, SQLException {
    AccountData accountData = m.getAccountData();
    if (profileEntry == null) {
      logger.debug("refresh job scheduled for address with no stored profile key. skipping");
      return;
    }

    if (!needsRefresh()) {
      return;
    }

    ProfileAndCredential profileAndCredential;
    SignalServiceProfile.RequestType requestType = SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
    Optional<ProfileKey> profileKeyOptional = Optional.fromNullable(profileEntry.getProfileKey());
    SignalServiceAddress address = profileEntry.getServiceAddress();
    profileAndCredential = m.getMessageReceiver().retrieveProfile(address, profileKeyOptional, Optional.absent(), requestType).get(10, TimeUnit.SECONDS);

    long now = System.currentTimeMillis();
    final ProfileKeyCredential profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
    Recipient recipient = m.getRecipientsTable().get(profileEntry.getServiceAddress());
    final SignalProfile profile = m.decryptProfile(recipient, profileEntry.getProfileKey(), profileAndCredential.getProfile());
    final ProfileAndCredentialEntry.UnidentifiedAccessMode unidentifiedAccessMode =
        getUnidentifiedAccessMode(profile.getUnidentifiedAccess(), profile.isUnrestrictedUnidentifiedAccess());

    accountData.profileCredentialStore.update(profileEntry.getServiceAddress(), profileEntry.getProfileKey(), now, profile, profileKeyCredential, unidentifiedAccessMode);
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
