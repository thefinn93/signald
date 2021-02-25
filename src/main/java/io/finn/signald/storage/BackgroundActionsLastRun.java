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

package io.finn.signald.storage;

import io.finn.signald.db.AccountDataTable;

import java.sql.SQLException;
import java.util.UUID;

import static io.finn.signald.db.AccountDataTable.Key.LAST_PRE_KEY_REFRESH;

public class BackgroundActionsLastRun {
  public long lastPreKeyRefresh;

  public void migrateToDB(UUID uuid) throws SQLException {
    AccountDataTable.set(uuid, LAST_PRE_KEY_REFRESH, lastPreKeyRefresh);
    lastPreKeyRefresh = 0;
  }
}
