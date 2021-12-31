/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
