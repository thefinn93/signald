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
import io.finn.signald.db.AccountsTable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Deprecated(1641027661)
public class JsonAccountList {
  public List<JsonAccount> accounts = new ArrayList<JsonAccount>();

  JsonAccountList() throws SQLException, NoSuchAccountError {
    for (UUID account : AccountsTable.getAll()) {
      if (account != null) {
        accounts.add(new JsonAccount(new Account(account)));
      }
    }
  }
}
