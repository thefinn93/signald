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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.RegistrationManager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.AccountDataTable;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;

@Doc("A local account in signald")
public class Account {
  @Doc(
      "The Signal device ID. Official Signal mobile clients (iPhone and Android) have device ID = 1, while linked devices such as Signal Desktop or Signal iPad have higher device IDs.")
  @JsonProperty("device_id")
  public int deviceID;
  @Doc("The primary identifier on the account, included with all requests to signald for this account. Previously called 'username'")
  @JsonProperty("account_id")
  public String accountID;

  @Doc("The address of this account") public JsonAddress address;

  @Doc("indicates the account has not completed registration") public Boolean pending;

  public Account(UUID accountUUID) throws NoSuchAccountError, InternalError { this(ACI.from(accountUUID)); }

  public Account(ACI aci) throws NoSuchAccountError, InternalError {
    try {
      try {
        accountID = AccountsTable.getE164(aci);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      }
      deviceID = AccountDataTable.getInt(aci, AccountDataTable.Key.DEVICE_ID);
    } catch (SQLException e) {
      throw new InternalError("error resolving account e164", e);
    }
    address = new JsonAddress(accountID, aci);
  }

  public Account(RegistrationManager m) {
    accountID = m.getE164();
    pending = true;
  }
}
