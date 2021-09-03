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
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllIdentityKeyList {
  @JsonProperty("identity_keys") List<IdentityKeyList> identityKeys;

  public AllIdentityKeyList(Recipient self, org.whispersystems.libsignal.IdentityKey ownKey, List<IdentityKeysTable.IdentityKeyRow> entireIdentityDB)
      throws IOException, SQLException {
    Map<String, IdentityKeyList> keyMap = new HashMap<>();
    RecipientsTable recipientsTable = self.getTable();
    for (IdentityKeysTable.IdentityKeyRow row : entireIdentityDB) {
      if (!keyMap.containsKey(row.getAddress().toString())) {
        Recipient recipient = recipientsTable.get(row.getAddress());
        keyMap.put(row.getAddress().toString(), new IdentityKeyList(self, ownKey, recipient, null));
      }
      keyMap.get(row.getAddress().toString()).addKey(row);
    }
    identityKeys = new ArrayList<>(keyMap.values());
  }
}
