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

package io.finn.signald.actions;

import io.finn.signald.Manager;
import io.finn.signald.storage.AccountData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class RefreshPreKeysAction implements Action {
  public static long INTERVAL = TimeUnit.DAYS.toMillis(3);
  private static Logger logger = LogManager.getLogger();

  @Override
  public void run(Manager m) throws IOException {
    AccountData accountData = m.getAccountData();
    if (m.getAccountManager().getPreKeysCount() < Manager.PREKEY_MINIMUM_COUNT) {
      logger.info("insufficient number of pre keys available, refreshing");
      m.refreshPreKeys();
    }
    accountData.backgroundActionsLastRun.lastPreKeyRefresh = System.currentTimeMillis();
    accountData.save();
  }

  @Override
  public String getName() {
    return RefreshPreKeysAction.class.getName();
  }
}
