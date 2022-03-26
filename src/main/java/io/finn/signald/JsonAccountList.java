/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.Database;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.signalservice.api.push.ACI;

@Deprecated(1641027661)
public class JsonAccountList {
  public List<JsonAccount> accounts = new ArrayList<JsonAccount>();

  JsonAccountList() throws SQLException, NoSuchAccountError {
    for (ACI aci : Database.Get().AccountsTable.getAll()) {
      accounts.add(new JsonAccount(new Account(aci)));
    }
  }
}
