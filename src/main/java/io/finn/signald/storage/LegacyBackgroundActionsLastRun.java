/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import io.finn.signald.Account;
import java.sql.SQLException;

@Deprecated
public class LegacyBackgroundActionsLastRun {
  public long lastPreKeyRefresh;

  public void migrateToDB(Account account) throws SQLException {
    account.setLastPreKeyRefresh(lastPreKeyRefresh);
    lastPreKeyRefresh = 0;
  }
}
