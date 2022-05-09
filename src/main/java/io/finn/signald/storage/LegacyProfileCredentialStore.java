/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.IProfileCapabilitiesTable;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
@Deprecated
public class LegacyProfileCredentialStore {
  private final static Logger logger = LogManager.getLogger();
  private static boolean unsaved = false;
  public final List<LegacyProfileAndCredentialEntry> profiles = new ArrayList<>();
  @JsonIgnore private Recipient self;

  public boolean migrateToDB(Account account) throws SQLException, IOException {
    Database db = account.getDB();
    if (profiles.size() > 0) {
      logger.info("Migrating {} profiles to database", profiles.size());
    }
    for (LegacyProfileAndCredentialEntry entry : profiles) {
      Recipient r = db.RecipientsTable.get(entry.getServiceAddress());
      db.ProfileKeysTable.setProfileKey(r, entry.getProfileKey());
      db.ProfileKeysTable.setProfileKeyCredential(r, entry.getProfileKeyCredential());
      db.ProfileKeysTable.setUnidentifiedAccessMode(r, entry.getUnidentifiedAccessMode().migrate());
      db.ProfileKeysTable.setRequestPending(r, entry.isRequestPending());

      LegacySignalProfile profile = entry.getProfile();
      if (profile != null) {
        db.ProfilesTable.setSerializedName(r, profile.getName());
        db.ProfilesTable.setEmoji(r, profile.getEmoji());
        db.ProfilesTable.setAbout(r, profile.getAbout());
        db.ProfilesTable.setPaymentAddress(r, profile.getPaymentAddress());

        db.ProfileCapabilitiesTable.set(r, new IProfileCapabilitiesTable.Capabilities(profile.getCapabilities()));
      }
    }
    return true;
  }

  void initialize(Recipient self) { this.self = self; }

  @Deprecated
  public LegacyProfileAndCredentialEntry storeProfileKey(Recipient recipient, ProfileKey profileKey) {
    return storeProfileKey(recipient.getAddress(), profileKey);
  }

  @Deprecated
  public LegacyProfileAndCredentialEntry storeProfileKey(SignalServiceAddress owner, ProfileKey profileKey) {
    LegacyProfileAndCredentialEntry newEntry =
        new LegacyProfileAndCredentialEntry(owner, profileKey, 0, null, null, LegacyProfileAndCredentialEntry.UnidentifiedAccessMode.UNKNOWN);
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
  @Deprecated
  public LegacyProfileAndCredentialEntry storeProfileKeyIfAbsent(SignalServiceAddress owner, ProfileKey profileKey) {
    LegacyProfileAndCredentialEntry newEntry =
        new LegacyProfileAndCredentialEntry(owner, profileKey, 0, null, null, LegacyProfileAndCredentialEntry.UnidentifiedAccessMode.UNKNOWN);
    synchronized (profiles) {
      for (LegacyProfileAndCredentialEntry profileEntry : profiles) {
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

  @Deprecated
  public LegacyProfileAndCredentialEntry update(SignalServiceAddress address, ProfileKey profileKey, long now, LegacySignalProfile profile,
                                                ProfileKeyCredential profileKeyCredential, LegacyProfileAndCredentialEntry.UnidentifiedAccessMode unidentifiedAccessMode) {
    LegacyProfileAndCredentialEntry entry = new LegacyProfileAndCredentialEntry(address, profileKey, now, profile, profileKeyCredential, unidentifiedAccessMode);
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
