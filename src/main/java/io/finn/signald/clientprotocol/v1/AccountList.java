/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.Database;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccountList {
  public final List<Account> accounts;
  public AccountList() throws NoSuchAccountError, InternalError {
    accounts = new ArrayList<>();
    try {
      for (UUID uuid : Database.Get().AccountsTable.getAll()) {
        accounts.add(new Account(uuid));
      }
    } catch (SQLException e) {
      throw new InternalError("error resolving account e164", e);
    }
  }
}
