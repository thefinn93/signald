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

package io.finn.signald.actions;

import io.finn.signald.Manager;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.storage.SignalProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RefreshProfileAction implements Action {
  private static final Logger logger = LogManager.getLogger();
  public static final long PROFILE_REFRESH_INTERVAL = TimeUnit.HOURS.toMillis(12);

  public ProfileAndCredentialEntry profileEntry;

  // get ProfileAndCredentialEntry with accountData.profileCredentialStore.get(address);
  public RefreshProfileAction(ProfileAndCredentialEntry p) { profileEntry = p; }

  public boolean needsRefresh() {
    return profileEntry.getProfileKeyCredential() == null || System.currentTimeMillis() - profileEntry.getLastUpdateTimestamp() > PROFILE_REFRESH_INTERVAL;
  }

  @Override
  public void run(Manager m) throws InterruptedException, ExecutionException, TimeoutException, IOException {
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
    profileAndCredential = m.getMessageReceiver().retrieveProfile(profileEntry.getServiceAddress(), profileKeyOptional, Optional.absent(), requestType).get(10, TimeUnit.SECONDS);

    long now = System.currentTimeMillis();
    final ProfileKeyCredential profileKeyCredential = profileAndCredential.getProfileKeyCredential().orNull();
    final SignalProfile profile = m.decryptProfile(profileEntry.getProfileKey(), profileAndCredential.getProfile());
    accountData.profileCredentialStore.update(profileEntry.getServiceAddress(), profileEntry.getProfileKey(), now, profile, profileKeyCredential);
  }

  @Override
  public String getName() {
    return RefreshProfileAction.class.getName();
  }
}
