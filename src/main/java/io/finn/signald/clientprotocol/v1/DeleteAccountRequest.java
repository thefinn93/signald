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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import java.io.IOException;
import java.sql.SQLException;

@ProtocolType("delete_account")
@Doc(
    "delete all account data signald has on disk, and optionally delete the account from the server as well. Note that this is not \"unlink\" and will delete the entire account, even from a linked device.")
public class DeleteAccountRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to delete") @Required public String account;

  @Doc("delete account information from the server as well (default false)") public boolean server = false;

  @Override
  public Empty run(Request request) throws SQLException, IOException, NoSuchAccount {
    Manager m = Utils.getManager(account);
    m.deleteAccount(server);
    return new Empty();
  }
}
