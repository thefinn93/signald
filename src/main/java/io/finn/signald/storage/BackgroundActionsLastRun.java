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

import io.finn.signald.Account;
import java.sql.SQLException;
import java.util.UUID;

public class BackgroundActionsLastRun {
  public long lastPreKeyRefresh;

  public void migrateToDB(UUID uuid) throws SQLException {
    new Account(uuid).setLastPreKeyRefresh(lastPreKeyRefresh);
    lastPreKeyRefresh = 0;
  }
}
