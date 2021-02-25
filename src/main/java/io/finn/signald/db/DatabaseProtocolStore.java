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

package io.finn.signald.db;

import io.finn.signald.exceptions.InvalidAddressException;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceProtocolStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class DatabaseProtocolStore implements SignalServiceProtocolStore {
  private final PreKeysTable preKeys;
  private final SessionsTable sessions;
  private final SignedPreKeysTable signedPrekeys;
  private final IdentityKeysTable identityKeys;

  public DatabaseProtocolStore(UUID uuid) {
    preKeys = new PreKeysTable(uuid);
    sessions = new SessionsTable(uuid);
    signedPrekeys = new SignedPreKeysTable(uuid);
    identityKeys = new IdentityKeysTable(uuid);
  }

  public DatabaseProtocolStore(String identifier) {
    UUID uuid = null;
    preKeys = new PreKeysTable(uuid);
    sessions = new SessionsTable(uuid);
    signedPrekeys = new SignedPreKeysTable(uuid);
    identityKeys = new IdentityKeysTable(identifier);
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeys.getIdentityKeyPair();
  }

  @Override
  public int getLocalRegistrationId() {
    return identityKeys.getLocalRegistrationId();
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return identityKeys.saveIdentity(address, identityKey);
  }

  public boolean saveIdentity(String identifier, IdentityKey key, TrustLevel level) { return identityKeys.saveIdentity(identifier, key, level); }

  public boolean saveIdentity(SignalServiceAddress identifier, IdentityKey key, TrustLevel level) { return identityKeys.saveIdentity(identifier, key, level); }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return identityKeys.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return identityKeys.getIdentity(address);
  }

  public List<IdentityKeysTable.IdentityKeyRow> getIdentities(SignalServiceAddress address) throws SQLException, InvalidKeyException, InvalidAddressException {
    return identityKeys.getIdentities(address);
  }

  public List<IdentityKeysTable.IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException { return identityKeys.getIdentities(); }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    return preKeys.loadPreKey(preKeyId);
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    preKeys.storePreKey(preKeyId, record);
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return preKeys.containsPreKey(preKeyId);
  }

  @Override
  public void removePreKey(int preKeyId) {
    preKeys.removePreKey(preKeyId);
  }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress address) {
    return sessions.loadSession(address);
  }

  @Override
  public List<Integer> getSubDeviceSessions(String name) {
    return sessions.getSubDeviceSessions(name);
  }

  @Override
  public void storeSession(SignalProtocolAddress address, SessionRecord record) {
    sessions.storeSession(address, record);
  }

  @Override
  public boolean containsSession(SignalProtocolAddress address) {
    return sessions.containsSession(address);
  }

  @Override
  public void deleteSession(SignalProtocolAddress address) {
    sessions.deleteSession(address);
  }

  @Override
  public void deleteAllSessions(String name) {
    sessions.deleteAllSessions(name);
  }

  public void deleteAllSessions(SignalServiceAddress address) { sessions.deleteAllSessions(address); }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    return signedPrekeys.loadSignedPreKey(signedPreKeyId);
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    return signedPrekeys.loadSignedPreKeys();
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    signedPrekeys.storeSignedPreKey(signedPreKeyId, record);
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return signedPrekeys.containsSignedPreKey(signedPreKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    signedPrekeys.removeSignedPreKey(signedPreKeyId);
  }

  @Override
  public void archiveSession(SignalProtocolAddress address) {
    SessionRecord session = loadSession(address);
    session.archiveCurrentState();
    storeSession(address, session);
  }
}
