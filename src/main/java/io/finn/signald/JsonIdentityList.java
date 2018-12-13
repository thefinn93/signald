/**
 * Copyright (C) 2018 Finn Herzfeld
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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import org.asamk.signal.storage.protocol.JsonIdentityKeyStore;

class JsonIdentityList {
  public List<JsonIdentity> identities = new ArrayList<JsonIdentity>();

  JsonIdentityList(List<JsonIdentityKeyStore.Identity> identities, Manager m) {
    for(JsonIdentityKeyStore.Identity identity : identities) {
      this.identities.add(new JsonIdentity(identity, m));
    }
  }

  JsonIdentityList(String number, Manager m) {
    if(number == null) {
      for (Map.Entry<String, List<JsonIdentityKeyStore.Identity>> keys : m.getIdentities().entrySet()) {
        for (JsonIdentityKeyStore.Identity identity : keys.getValue()) {
            this.identities.add(new JsonIdentity(identity, m, keys.getKey()));
        }
      }
    } else {
      for(JsonIdentityKeyStore.Identity identity : m.getIdentities(number)) {
        this.identities.add(new JsonIdentity(identity, m, number));
      }
    }
  }
}
