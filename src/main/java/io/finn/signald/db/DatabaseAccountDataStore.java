/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import io.finn.signald.Account;
import io.finn.signald.Config;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public class DatabaseAccountDataStore implements SignalServiceAccountDataStore {
  private final IPreKeysTable preKeys;
  private final ISessionsTable sessions;
  private final ISignedPreKeysTable signedPrekeys;
  private final IIdentityKeysTable identityKeys;
  private final ISenderKeysTable senderKeys;
  private final ISenderKeySharedTable senderKeyShared;
  private final Account account;

  public DatabaseAccountDataStore(ACI aci) {
    preKeys = Database.Get(aci).PreKeysTable;
    sessions = Database.Get(aci).SessionsTable;
    signedPrekeys = Database.Get(aci).SignedPreKeysTable;
    identityKeys = Database.Get(aci).IdentityKeysTable;
    senderKeys = Database.Get(aci).SenderKeysTable;
    senderKeyShared = Database.Get(aci).SenderKeySharedTable;
    account = new Account(aci);
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

  public void handleUntrustedIdentityException(org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) throws SQLException, IOException {
    identityKeys.saveIdentity(e.getIdentifier(), e.getIdentityKey(), Config.getNewKeyTrustLevel());
    if (account == null) {
      return;
    }
    Recipient recipient = Database.Get(account.getACI()).RecipientsTable.get(e.getIdentifier());
    sessions.archiveAllSessions(recipient);
    senderKeyShared.deleteForAll(recipient);
  }

  public void archiveAllSessions(Recipient recipient) throws SQLException { sessions.archiveAllSessions(recipient); }

  public boolean saveIdentity(String identifier, IdentityKey key, TrustLevel level) throws IOException, SQLException { return identityKeys.saveIdentity(identifier, key, level); }

  public boolean saveIdentity(Recipient recipient, IdentityKey key, TrustLevel level) { return identityKeys.saveIdentity(recipient, key, level); }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return identityKeys.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return identityKeys.getIdentity(address);
  }

  public List<IIdentityKeysTable.IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException { return identityKeys.getIdentities(recipient); }

  public List<IIdentityKeysTable.IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException { return identityKeys.getIdentities(); }

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
  public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> list) throws NoSessionException {
    return sessions.loadExistingSessions(list);
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

  public void deleteAllSessions(Recipient recipient) { sessions.deleteAllSessions(recipient); }

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
    senderKeyShared.clearSenderKeySharedWith(Collections.singleton(address));
    storeSession(address, session);
  }

  @Override
  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> list) {
    return sessions.getAllAddressesWithActiveSessions(list);
  }

  @Override
  public void storeSenderKey(SignalProtocolAddress signalProtocolAddress, UUID distributionId, SenderKeyRecord senderKeyRecord) {
    senderKeys.storeSenderKey(signalProtocolAddress, distributionId, senderKeyRecord);
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress signalProtocolAddress, UUID distributionId) {
    return senderKeys.loadSenderKey(signalProtocolAddress, distributionId);
  }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    return senderKeyShared.getSenderKeySharedWith(distributionId);
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    senderKeyShared.markSenderKeySharedWith(distributionId, addresses);
  }

  @Override
  public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> collection) {
    senderKeyShared.clearSenderKeySharedWith(collection);
  }

  @Override
  public boolean isMultiDevice() {
    return senderKeyShared.isMultiDevice();
  }

  public boolean isCurrentRatchetKey(Recipient source, int sourceDeviceId, ECPublicKey key) {
    SessionRecord session = sessions.loadSession(new SignalProtocolAddress(source.getAddress().getIdentifier(), sourceDeviceId));
    if (session == null) {
      return false;
    }
    return session.currentRatchetKeyMatches(key);
  }
}
