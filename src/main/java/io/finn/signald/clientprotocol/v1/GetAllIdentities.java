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

import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.InvalidAddressException;
import io.finn.signald.exceptions.NoSuchAccountException;
import org.whispersystems.libsignal.InvalidKeyException;

import java.io.IOException;
import java.sql.SQLException;

@Doc("get all known identity keys")
@SignaldClientRequest(type = "get_all_identities")
public class GetAllIdentities implements RequestType<AllIdentityKeyList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Override
  public AllIdentityKeyList run(Request request) throws SQLException, IOException, NoSuchAccountException, InvalidAddressException, InvalidKeyException {
    Manager m = Manager.get(account);
    return new AllIdentityKeyList(m.getOwnAddress(), m.getIdentity(), m.getIdentities());
  }
}
