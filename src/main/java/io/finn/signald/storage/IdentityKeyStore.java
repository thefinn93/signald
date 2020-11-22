/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.util.AddressUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class IdentityKeyStore implements org.whispersystems.libsignal.state.IdentityKeyStore {
  private static final Logger logger = LogManager.getLogger();

  private AddressResolver resolver;

  public List<IdentityKeyStore.Identity> trustedKeys = new ArrayList<>();
  IdentityKeyPair identityKeyPair;
  public int registrationId;

  public IdentityKeyStore() {}

  public IdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId, AddressResolver resolver) {
    this.identityKeyPair = identityKeyPair;
    this.registrationId = localRegistrationId;
    this.resolver = resolver;
  }

  public void setResolver(final AddressResolver resolver) { this.resolver = resolver; }

  @Override
  @JsonIgnore
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyPair;
  }

  @Override
  @JsonIgnore
  public int getLocalRegistrationId() {
    return registrationId;
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress protocolAddress, IdentityKey identityKey) {
    return saveIdentity(protocolAddress.getName(), identityKey, TrustLevel.TRUSTED_UNVERIFIED);
  }

  public boolean saveIdentity(String identifier, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(resolver.resolve(identifier), identityKey, trustLevel); }
  public boolean saveIdentity(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(address, identityKey, trustLevel, null); }

  private List<Identity> getKeys(SignalProtocolAddress address) { return getKeys(address.getName()); }

  private List<Identity> getKeys(String identifier) { return getKeys(resolver.resolve(identifier)); }

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

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    // TODO implement possibility for different handling of incoming/outgoing trust decisions
    List<IdentityKeyStore.Identity> identities = getKeys(address);
    if (identities.size() == 0) {
      // Trust on first use
      return true;
    }

    for (IdentityKeyStore.Identity id : identities) {
      if (id.identityKey.equals(identityKey)) {
        return id.isTrusted();
      }
    }

    return false;
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    List<IdentityKeyStore.Identity> identities = getKeys(address);
    if (identities.size() == 0) {
      return null;
    }

    long maxDate = 0;
    IdentityKeyStore.Identity maxIdentity = null;
    for (IdentityKeyStore.Identity id : identities) {
      final long time = id.getDateAdded().getTime();
      if (maxIdentity == null || maxDate <= time) {
        maxDate = time;
        maxIdentity = id;
      }
    }
    return maxIdentity.getKey();
  }

  @JsonIgnore
  public List<Identity> getIdentities() {
    return trustedKeys;
  }

  public List<IdentityKeyStore.Identity> getIdentities(SignalServiceAddress address) { return getKeys(address); }

  // Getters and setters for Jackson
  @JsonSetter("identityKey")
  public void setIdentityKey(String identityKey) throws IOException, InvalidKeyException {
    identityKeyPair = new IdentityKeyPair(org.whispersystems.util.Base64.decode(identityKey));
  }

  @JsonGetter("identityKey")
  public String getIdentityKeyPairJSON() {
    if (identityKeyPair == null) {
      return null;
    }
    return org.whispersystems.util.Base64.encodeBytes(identityKeyPair.serialize());
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

    @JsonSetter("identityKey")
    public String getIdentityKey() {
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
        return trustLevel.TRUSTED_UNVERIFIED.name();
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
