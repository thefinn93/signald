/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.db.Recipient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ProfileCredentialStore {
  private static boolean unsaved = false;
  public final List<ProfileAndCredentialEntry> profiles = new ArrayList<>();

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

  public ProfileAndCredentialEntry storeProfileKey(Recipient recipient, ProfileKey profileKey) {
    ProfileAndCredentialEntry newEntry = new ProfileAndCredentialEntry(recipient.getAddress(), profileKey, 0, null, null, ProfileAndCredentialEntry.UnidentifiedAccessMode.UNKNOWN);
    synchronized (profiles) {
      for (int i = 0; i < profiles.size(); i++) {
        if (profiles.get(i).getServiceAddress().matches(recipient.getAddress())) {
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
