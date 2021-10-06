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
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidAddressException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.libsignal.InvalidKeyException;

@Deprecated(1641027661)
class JsonIdentityList {
  public List<JsonIdentity> identities = new ArrayList<>();

  JsonIdentityList(List<IdentityKeysTable.IdentityKeyRow> identities, Manager m) throws IOException, SQLException {
    for (IdentityKeysTable.IdentityKeyRow identity : identities) {
      this.identities.add(new JsonIdentity(identity, m));
    }
  }

  JsonIdentityList(Recipient recipient, Manager m) throws SQLException, InvalidKeyException, InvalidAddressException, IOException {
    if (recipient == null) {
      for (IdentityKeysTable.IdentityKeyRow identity : m.getIdentities()) {
        this.identities.add(new JsonIdentity(identity, m));
      }
    } else {
      List<IdentityKeysTable.IdentityKeyRow> identities = m.getIdentities(recipient);
      if (identities != null) {
        for (IdentityKeysTable.IdentityKeyRow identity : identities) {
          this.identities.add(new JsonIdentity(identity, m, recipient));
        }
      }
    }
  }
}
