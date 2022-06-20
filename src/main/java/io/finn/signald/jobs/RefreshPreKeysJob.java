/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.ServiceConfig;
import io.finn.signald.db.DatabaseAccountDataStore;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.Medium;
import org.whispersystems.signalservice.api.push.ServiceIdType;

public class RefreshPreKeysJob implements Job {
  public static long INTERVAL = TimeUnit.DAYS.toMillis(3);
  private static final Logger logger = LogManager.getLogger();

  private final Account account;

  public RefreshPreKeysJob(Account account) { this.account = account; }

  @Override
  public void run() throws SQLException, NoSuchAccountException, ServerNotFoundException, IOException, InvalidProxyException, InvalidKeyException {
    long lastRefresh = account.getLastPreKeyRefresh();
    if (lastRefresh <= 0) {
      logger.info("generating pre keys");
      refreshPreKeys(ServiceIdType.ACI);
      refreshPreKeys(ServiceIdType.PNI);
    } else if (account.getSignalDependencies().getAccountManager().getPreKeysCount(ServiceIdType.ACI) < ServiceConfig.PREKEY_MINIMUM_COUNT) {
      logger.info("insufficient number of pre keys available, refreshing");
      refreshPreKeys(ServiceIdType.ACI);
      refreshPreKeys(ServiceIdType.PNI);
    }
    account.setLastPreKeyRefreshNow();
  }

  public static void runIfNeeded(Account account) throws SQLException, IOException, InvalidKeyException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    long lastRefresh = account.getLastPreKeyRefresh();
    if (System.currentTimeMillis() - lastRefresh > INTERVAL) {
      RefreshPreKeysJob job = new RefreshPreKeysJob(account);
      job.run();
    }
  }

  private void refreshPreKeys(ServiceIdType serviceIdType)
      throws IOException, SQLException, InvalidKeyException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    if (serviceIdType != ServiceIdType.ACI) {
      // TODO implement
      return;
    }
    List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
    SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(account.getACIIdentityKeyPair());
    IdentityKeyPair identityKeyPair = account.getACIIdentityKeyPair();
    account.getSignalDependencies().getAccountManager().setPreKeys(serviceIdType, identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
  }

  private List<PreKeyRecord> generatePreKeys() throws SQLException {
    List<PreKeyRecord> records = new LinkedList<>();

    DatabaseAccountDataStore protocolStore = account.getProtocolStore();
    for (int i = 0; i < ServiceConfig.PREKEY_BATCH_SIZE; i++) {
      int preKeyId = (account.getPreKeyIdOffset() + i) % Medium.MAX_VALUE;
      ECKeyPair keyPair = Curve.generateKeyPair();
      PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

      protocolStore.storePreKey(preKeyId, record);
      records.add(record);
    }

    account.setPreKeyIdOffset((account.getPreKeyIdOffset() + ServiceConfig.PREKEY_BATCH_SIZE + 1) % Medium.MAX_VALUE);

    return records;
  }

  private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKey) throws SQLException, InvalidKeyException {
    ECKeyPair keyPair = Curve.generateKeyPair();
    byte[] signature = Curve.calculateSignature(identityKey.getPrivateKey(), keyPair.getPublicKey().serialize());
    int signedPreKeyId = account.getNextSignedPreKeyId();
    SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
    account.getProtocolStore().storeSignedPreKey(signedPreKeyId, record);
    account.setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);
    return record;
  }
}
