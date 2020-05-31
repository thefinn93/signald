/*
 * Copyright (C) 2020 Finn Herzfeld
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

import io.finn.signald.storage.IdentityKeyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class JsonIdentityList {
  public List<JsonIdentity> identities = new ArrayList<>();

  JsonIdentityList(List<IdentityKeyStore.Identity> identities, Manager m) {
    for(IdentityKeyStore.Identity identity : identities) {
      this.identities.add(new JsonIdentity(identity, m));
    }
  }

  JsonIdentityList(String number, Manager m) {
    if(number == null) {
      for (Map.Entry<String, List<IdentityKeyStore.Identity>> keys : m.getIdentities().entrySet()) {
        for (IdentityKeyStore.Identity identity : keys.getValue()) {
            this.identities.add(new JsonIdentity(identity, m, keys.getKey()));
        }
      }
    } else {
      List<IdentityKeyStore.Identity> identities = m.getIdentities(number);
      if(identities != null) {
        for(IdentityKeyStore.Identity identity : m.getIdentities(number)) {
          this.identities.add(new JsonIdentity(identity, m, number));
        }
      }
    }
  }
}
