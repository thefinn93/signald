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

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;
import java.util.UUID;

@Doc("Resolve a partial JsonAddress with only a number or UUID to one with both. Anywhere that signald accepts a JsonAddress will except a partial, this is a convenience "
     + "function for client authors, mostly because signald doesn't resolve all the partials it returns.")
@ProtocolType("resolve_address")
public class ResolveAddressRequest implements RequestType<JsonAddress> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The signal account to use") @Required public String account;

  @Doc("The partial address, missing fields") @Required public JsonAddress partial;

  @Override
  public JsonAddress run(Request request) throws InternalError, NoSuchAccountError {
    UUID accountUUID;
    try {
      accountUUID = AccountsTable.getUUID(account);
    } catch (SQLException e) {
      throw new InternalError("error getting account UUID", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }
    return new JsonAddress(Common.getRecipient(new RecipientsTable(accountUUID), partial));
  }
}
