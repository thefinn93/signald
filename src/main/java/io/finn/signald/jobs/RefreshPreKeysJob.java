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

package io.finn.signald.jobs;

import static io.finn.signald.db.AccountDataTable.Key.LAST_PRE_KEY_REFRESH;

import io.finn.signald.Manager;
import io.finn.signald.ServiceConfig;
import io.finn.signald.db.AccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyException;

public class RefreshPreKeysJob implements Job {
  public static long INTERVAL = TimeUnit.DAYS.toMillis(3);
  private static final Logger logger = LogManager.getLogger();

  private final UUID uuid;

  public RefreshPreKeysJob(UUID u) { uuid = u; }

  @Override
  public void run() throws IOException, SQLException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(uuid);
    runWithManager(m);
  }

  public static void runIfNeeded(UUID uuid, Manager m) throws SQLException, IOException {
    long lastRefresh = AccountDataTable.getLong(uuid, LAST_PRE_KEY_REFRESH);
    if (System.currentTimeMillis() - lastRefresh > INTERVAL) {
      RefreshPreKeysJob job = new RefreshPreKeysJob(uuid);
      job.runWithManager(m);
    }
  }

  private void runWithManager(Manager m) throws IOException, SQLException {
    long lastRefresh = AccountDataTable.getLong(uuid, LAST_PRE_KEY_REFRESH);
    if (lastRefresh <= 0) {
      logger.info("generating pre keys");
      m.refreshPreKeys();
    } else if (m.getAccountManager().getPreKeysCount() < ServiceConfig.PREKEY_MINIMUM_COUNT) {
      logger.info("insufficient number of pre keys available, refreshing");
      m.refreshPreKeys();
    }
    AccountDataTable.set(uuid, LAST_PRE_KEY_REFRESH, System.currentTimeMillis());
  }
}
