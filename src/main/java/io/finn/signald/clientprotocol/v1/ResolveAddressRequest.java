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
import io.finn.signald.annotations.Required;
import io.finn.signald.annotations.SignaldClientRequest;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;

@Doc(
    "Resolve a partial JsonAddress with only a number or UUID to one with both. Anywhere that signald accepts a JsonAddress will except a partial, this is a convenience function for client authors, mostly because signald doesn't resolve all the partials it returns")
@SignaldClientRequest(type = "resolve_address", ResponseClass = JsonAddress.class)
public class ResolveAddressRequest implements RequestType {
  @Doc("The signal account to use") @Required String account;

  @Doc("The partial address, missing fields") @Required JsonAddress partial;

  @Override
  public void run(Request request) throws IOException, NoSuchAccountException {
    SignalServiceAddress resolved = Manager.get(account).getResolver().resolve(partial.getSignalServiceAddress());
    request.reply(new JsonAddress(resolved));
  }
}
