/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.finn.signald.Account;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.util.AddressUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

public class IdentityKeyStore {
  private static final Logger logger = LogManager.getLogger();

  public List<IdentityKeyStore.Identity> trustedKeys = new ArrayList<>();
  IdentityKeyPair identityKeyPair;
  public int registrationId;

  public IdentityKeyStore() {}

  public IdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
    this.identityKeyPair = identityKeyPair;
    this.registrationId = localRegistrationId;
  }

  public boolean saveIdentity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(address, identityKey, trustLevel, null); }

  private List<Identity> getKeys(SignalServiceAddress other) {
    List<Identity> matches = new ArrayList<>();
    for (Identity key : trustedKeys) {
      if (key.address == null) {
        logger.warn("Key has no address! This may indicate a corrupt data file.");
        continue;
      }
      if (key.address.matches(other)) {
        matches.add(key);
      }
    }
    return matches;
  }

  /**
   * Adds or updates the given identityKey for the user name and sets the trustLevel and added timestamp.
   *
   * @param address     the user's address
   * @param identityKey The user's public key
   * @param trustLevel
   * @param added       Added timestamp, if null and the key is newly added, the current time is used.
   */
  public boolean saveIdentity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
    List<IdentityKeyStore.Identity> identities = getKeys(address);

    for (IdentityKeyStore.Identity id : identities) {
      if (!id.identityKey.equals(identityKey)) {
        continue;
      }
      if (id.trustLevel != null && id.trustLevel.compareTo(trustLevel) < 0) {
        id.trustLevel = trustLevel;
      }
      if (added != null) {
        id.added = added;
      }

      return true;
    }
    trustedKeys.add(new Identity(address, identityKey, trustLevel, added != null ? added : new Date()));
    return false;
  }

  @JsonIgnore
  public List<Identity> getIdentities() {
    return trustedKeys;
  }

  public List<IdentityKeyStore.Identity> getIdentities(SignalServiceAddress address) { return getKeys(address); }

  public void migrateToDB(Account account) throws SQLException, IOException {
    var table = Database.Get(account.getACI()).IdentityKeysTable;
    logger.info("migrating " + trustedKeys.size() + " identity keys to the database");
    Iterator<Identity> iterator = trustedKeys.iterator();
    while (iterator.hasNext()) {
      Identity entry = iterator.next();
      if (entry.identityKey == null) {
        continue;
      }
      table.saveIdentity(Database.Get(account.getACI()).RecipientsTable.get(entry.address), entry.identityKey, entry.trustLevel, entry.added);
      iterator.remove();
    }

    if (identityKeyPair != null) {
      account.setACIIdentityKeyPair(identityKeyPair);
      identityKeyPair = null;
    }

    if (registrationId > 0) {
      account.setLocalRegistrationId(registrationId);
      registrationId = 0;
    }
  }

  // Getters and setters for Jackson
  @JsonSetter("identityKey")
  public void setIdentityKey(String identityKey) throws IOException {
    identityKeyPair = new IdentityKeyPair(org.whispersystems.util.Base64.decode(identityKey));
  }

  public static class Identity {
    JsonAddress address;
    IdentityKey identityKey;
    TrustLevel trustLevel;
    Date added;

    public Identity() {}

    Identity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
      this.address = new JsonAddress(address);
      this.identityKey = identityKey;
      this.trustLevel = trustLevel;
      this.added = added;
    }

    public Identity(IdentityKey key) { identityKey = key; }

    boolean isTrusted() { return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED; }

    @JsonIgnore
    public IdentityKey getKey() {
      return this.identityKey;
    }

    @JsonIgnore
    public TrustLevel getTrustLevel() {
      return this.trustLevel;
    }

    @JsonIgnore
    public Date getDateAdded() {
      return this.added;
    }

    @JsonIgnore
    public byte[] getFingerprint() {
      return identityKey.getPublicKey().serialize();
    }

    public JsonAddress getAddress() { return address; }

    // Jackson getters and setters
    public void setName(String name) { address = new JsonAddress(AddressUtil.fromIdentifier(name)); }

    @JsonSetter("identityKey")
    public void setIdentityKey(String identityKey) throws IOException, InvalidKeyException {
      this.identityKey = new IdentityKey(org.whispersystems.util.Base64.decode(identityKey), 0);
    }

    @JsonGetter("identityKey")
    public String getIdentityKey() {
      if (identityKey == null) {
        return null;
      }
      return Base64.encodeBytes(identityKey.serialize());
    }

    public void setAddedTimestamp(long added) {
      if (added == 0) {
        return;
      }
      this.added = new Date(added);
    }

    public long getAddedTimestamp() {
      if (added == null) {
        return 0;
      }
      return added.getTime();
    }

    @JsonGetter("trust")
    public String getTrustLevelString() {
      if (trustLevel == null) {
        return TrustLevel.TRUSTED_UNVERIFIED.name();
      }
      return trustLevel.name();
    }

    @JsonSetter("trust")
    public void setTrustLevelString(String level) {
      trustLevel = TrustLevel.valueOf(level);
    }

    @JsonSetter("trustLevel")
    public void setTrustLevel(int level) {
      trustLevel = TrustLevel.fromInt(level);
    }
  }
}
