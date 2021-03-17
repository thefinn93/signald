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
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.storage.IdentityKeyStore;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@Doc("Get information about a known keys for a particular address")
@SignaldClientRequest(type = "get_identities")
public class GetIdentitiesRequest implements RequestType<IdentityKeyList> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("address to get keys for") @Required public JsonAddress address;

  @Override
  public IdentityKeyList run(Request request) throws SQLException, IOException, NoSuchAccountException {
    Manager m = Manager.get(account);
    List<IdentityKeyStore.Identity> identities = m.getIdentities(address.getSignalServiceAddress());
    SignalServiceAddress addr = m.getResolver().resolve(address.getSignalServiceAddress());
    return new IdentityKeyList(m.getOwnAddress(), m.getIdentity(), addr, identities);
  }
}
