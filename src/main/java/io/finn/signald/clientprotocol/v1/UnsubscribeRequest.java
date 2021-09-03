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
import io.finn.signald.MessageReceiver;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

@ProtocolType("unsubscribe")
@Doc("See subscribe for more info")
public class UnsubscribeRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to unsubscribe from") @Required public String account;

  @Override
  public Empty run(Request request) throws SQLException, IOException, NoSuchAccount {
    UUID accountUUID;
    try {
      accountUUID = AccountsTable.getUUID(account);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccount(e);
    }
    MessageReceiver.unsubscribe(accountUUID, request.getSocket());
    return new Empty();
  }
}
