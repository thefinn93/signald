/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
