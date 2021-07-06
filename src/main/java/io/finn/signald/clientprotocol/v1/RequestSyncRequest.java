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
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

@Doc("Request other devices on the account send us their group list, syncable config and contact list.")
@ProtocolType("request_sync")
public class RequestSyncRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @Doc("request group sync (default true)") public boolean groups = true;
  @Doc("request configuration sync (default true)") public boolean configuration = true;
  @Doc("request contact sync (default true)") public boolean contacts = true;
  @Doc("request block list sync (default true)") public boolean blocked = true;

  @Override
  public Empty run(Request request)
      throws SQLException, IOException, NoSuchAccount, UntrustedIdentityException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Utils.getManager(account);
    if (groups) {
      m.requestSyncGroups();
    }

    if (configuration) {
      m.requestSyncConfiguration();
    }

    if (contacts) {
      m.requestSyncConfiguration();
    }

    if (blocked) {
      m.requestSyncBlocked();
    }

    return new Empty();
  }
}
