/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.Account;
import io.finn.signald.ProfileKeySet;
import io.finn.signald.Util;
import io.finn.signald.db.Recipient;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.SyncStorageDataJob;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ProfileCredentialStore {
  private final static Logger logger = LogManager.getLogger();
  private static boolean unsaved = false;
  public final List<ProfileAndCredentialEntry> profiles = new ArrayList<>();
  @JsonIgnore private Recipient self;

  void initialize(Recipient self) { this.self = self; }

  @JsonIgnore
  public ProfileKeyCredential getProfileKeyCredential(UUID uuid) {
    return getProfileKeyCredential(ACI.from(uuid));
  }

  @JsonIgnore
  public ProfileKeyCredential getProfileKeyCredential(ACI aci) {
    ProfileAndCredentialEntry entry = get(new SignalServiceAddress(aci, Optional.absent()));
    if (entry != null) {
      return entry.getProfileKeyCredential();
    }
    return null;
  }

  @JsonIgnore
  public ProfileAndCredentialEntry get(Recipient recipient) {
    SignalServiceAddress address = recipient.getAddress();
    synchronized (profiles) {
      for (ProfileAndCredentialEntry entry : profiles) {
        if (entry.getServiceAddress().matches(address)) {
          return entry;
        }
      }
    }
    return null;
  }
  @JsonIgnore
  public ProfileAndCredentialEntry get(SignalServiceAddress address) {
    synchronized (profiles) {
      for (ProfileAndCredentialEntry entry : profiles) {
        if (entry.getServiceAddress() == null) {
          continue;
        }
        if (entry.getServiceAddress().matches(address)) {
          return entry;
        }
      }
    }
    return null;
  }

  @JsonIgnore
  public boolean isUnsaved() {
    return unsaved;
  }

  public void markSaved() { unsaved = false; }

  public ProfileAndCredentialEntry storeProfileKey(Recipient recipient, ProfileKey profileKey) { return storeProfileKey(recipient.getAddress(), profileKey); }

  public ProfileAndCredentialEntry storeProfileKey(SignalServiceAddress owner, ProfileKey profileKey) {
    ProfileAndCredentialEntry newEntry = new ProfileAndCredentialEntry(owner, profileKey, 0, null, null, ProfileAndCredentialEntry.UnidentifiedAccessMode.UNKNOWN);
    synchronized (profiles) {
      for (int i = 0; i < profiles.size(); i++) {
        if (profiles.get(i).getServiceAddress().matches(owner)) {
          if (!profiles.get(i).getProfileKey().equals(profileKey)) {
            profiles.set(i, newEntry);
            unsaved = true;
          }
          return newEntry;
        }
      }
      profiles.add(newEntry);
    }
    unsaved = true;
    return newEntry;
  }

  /**
   * @return a non-null entry iff the profile key was stored
   */
  public ProfileAndCredentialEntry storeProfileKeyIfAbsent(SignalServiceAddress owner, ProfileKey profileKey) {
    ProfileAndCredentialEntry newEntry = new ProfileAndCredentialEntry(owner, profileKey, 0, null, null, ProfileAndCredentialEntry.UnidentifiedAccessMode.UNKNOWN);
    synchronized (profiles) {
      for (ProfileAndCredentialEntry profileEntry : profiles) {
        // not sure if ProfileAndCredentialEntry can ever have null profileKeys
        if (profileEntry.getServiceAddress().matches(owner) && profileEntry.getProfileKey() != null) {
          return null;
        }
      }
      profiles.add(newEntry);
    }
    unsaved = true;
    return newEntry;
  }

  /**
   * Persists the given profile key set into the credential store. This method respects authoritative profile keys.
   *
   * @param profileKeySet A set of profile keys to persist, typically from an incoming group update.
   * @return The profile entries that were actually updated. Only authoritative profile keys can be used to update
   * profile keys for users that we already have, so this may be less than the actual number of keys in the input.
   */
  public Set<ProfileAndCredentialEntry> persistProfileKeySet(ProfileKeySet profileKeySet) {
    final Map<ACI, ProfileKey> profileKeys = profileKeySet.getProfileKeys();
    final Map<ACI, ProfileKey> authoritativeProfileKeys = profileKeySet.getAuthoritativeProfileKeys();
    final int totalKeys = profileKeys.size() + authoritativeProfileKeys.size();
    if (totalKeys == 0) {
      return Collections.emptySet();
    }

    logger.info("Persisting " + totalKeys + " profile keys, " + authoritativeProfileKeys.size() + " of which are authoritative");

    final var updated = new HashSet<ProfileAndCredentialEntry>(totalKeys);

    for (Map.Entry<ACI, ProfileKey> entry : profileKeys.entrySet()) {
      final ProfileAndCredentialEntry maybeNewEntry = storeProfileKeyIfAbsent(new SignalServiceAddress(entry.getKey()), entry.getValue());
      if (maybeNewEntry != null) {
        logger.debug("Learned new profile key for " + Util.redact(entry.getKey()));
        updated.add(maybeNewEntry);
      }
    }

    for (Map.Entry<ACI, ProfileKey> entry : authoritativeProfileKeys.entrySet()) {
      final var thisAddress = new SignalServiceAddress(entry.getKey());
      if (self.getAddress().matches(thisAddress)) {
        logger.info("Seen authoritative update for self");
        final var selfProfileKeyEntry = get(self);
        if (selfProfileKeyEntry != null && !selfProfileKeyEntry.getProfileKey().equals(entry.getValue())) {
          logger.warn("Seen authoritative update for self that didn't match local, scheduling storage sync");
          BackgroundJobRunnerThread.queue(new SyncStorageDataJob(new Account(self.getACI())));
        }
      } else {
        logger.debug("Profile key from owner for " + Util.redact(entry.getKey()));
        updated.add(storeProfileKey(thisAddress, entry.getValue()));
      }
    }

    return updated;
  }

  public ProfileAndCredentialEntry update(SignalServiceAddress address, ProfileKey profileKey, long now, SignalProfile profile, ProfileKeyCredential profileKeyCredential,
                                          ProfileAndCredentialEntry.UnidentifiedAccessMode unidentifiedAccessMode) {
    ProfileAndCredentialEntry entry = new ProfileAndCredentialEntry(address, profileKey, now, profile, profileKeyCredential, unidentifiedAccessMode);
    synchronized (profiles) {
      for (int i = 0; i < profiles.size(); i++) {
        if (profiles.get(i).getServiceAddress().matches(address)) {
          // TODO: announce profile change
          profiles.set(i, entry);
          unsaved = true;
          return entry;
        }
      }
      profiles.add(entry);
      unsaved = true;
      return entry;
    }
  }
}
