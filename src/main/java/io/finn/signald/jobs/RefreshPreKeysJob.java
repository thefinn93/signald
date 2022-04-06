/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.ServiceConfig;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceIdType;

public class RefreshPreKeysJob implements Job {
  public static long INTERVAL = TimeUnit.DAYS.toMillis(3);
  private static final Logger logger = LogManager.getLogger();

  private final ACI aci;

  public RefreshPreKeysJob(ACI aci) { this.aci = aci; }

  @Override
  public void run() throws IOException, SQLException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(aci);
    runWithManager(m);
  }

  public static void runIfNeeded(ACI aci, Manager m) throws SQLException, IOException, InvalidKeyException {
    long lastRefresh = new Account(aci).getLastPreKeyRefresh();
    if (System.currentTimeMillis() - lastRefresh > INTERVAL) {
      RefreshPreKeysJob job = new RefreshPreKeysJob(aci);
      job.runWithManager(m);
    }
  }

  private void runWithManager(Manager m) throws IOException, SQLException, InvalidKeyException {
    long lastRefresh = m.getAccount().getLastPreKeyRefresh();
    if (lastRefresh <= 0) {
      logger.info("generating pre keys");
      m.refreshPreKeys();
    } else if (m.getAccountManager().getPreKeysCount(ServiceIdType.ACI) < ServiceConfig.PREKEY_MINIMUM_COUNT) {
      logger.info("insufficient number of pre keys available, refreshing");
      m.refreshPreKeys();
    }
    m.getAccount().setLastPreKeyRefreshNow();
  }
}
