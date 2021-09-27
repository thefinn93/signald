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

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;

@Deprecated(1641027661)
class JsonAccount {
  public int deviceId;
  public String username;
  public String filename;
  public String uuid;
  public boolean registered;
  public boolean has_keys;
  public boolean subscribed;

  JsonAccount(Account account) throws SQLException, NoSuchAccountError {
    try {
      this.username = account.getE164();
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }
    this.deviceId = account.getDeviceId();
    this.filename = Manager.getFileName(username);
    if (account.getUUID() != null) {
      this.uuid = account.getUUID().toString();
    }
    this.registered = true;
  }

  JsonAccount(RegistrationManager m) {
    this.username = m.getE164();
    this.filename = Manager.getFileName(username);
    this.registered = false;
  }

  JsonAccount(Account a, boolean subscribed) throws SQLException, NoSuchAccountError {
    this(a);
    this.subscribed = subscribed;
  }
}
