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

import io.finn.signald.annotations.Doc;
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.storage.IdentityKeyStore;
import io.finn.signald.util.SafetyNumberHelper;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;

@Doc("a list of identity keys associated with a particular address")
public class IdentityKeyList {
  public JsonAddress address;
  public List<IdentityKey> identities;

  public IdentityKeyList(SignalServiceAddress ownAddress, org.whispersystems.libsignal.IdentityKey ownKey, SignalServiceAddress a,
                         List<IdentityKeysTable.IdentityKeyRow> identities) {
    this.address = new JsonAddress(a);
    this.identities = new ArrayList<>();
    for (IdentityKeysTable.IdentityKeyRow i : identities) {
      this.identities.add(new IdentityKey(i, SafetyNumberHelper.computeFingerprint(ownAddress, ownKey, a, i.getKey())));
    }
  }
}
