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
import io.finn.signald.util.SafetyNumberHelper;
import org.asamk.signal.util.Hex;

class JsonIdentity {
  public String trust_level;
  public long added;
  public String fingerprint;
  public String safety_number;
  public String username;

  JsonIdentity(IdentityKeyStore.Identity identity, Manager m) {
    this.trust_level = identity.getTrustLevel().name();
    this.added = identity.getDateAdded().getTime();
    this.fingerprint = Hex.toStringCondensed(identity.getFingerprint());
  }

  JsonIdentity(IdentityKeyStore.Identity identity, Manager m, String username) {
    this(identity, m);
    this.safety_number = SafetyNumberHelper.computeSafetyNumber(m.getUsername(), m.getIdentity(), username, identity.getIdentityKey());
    this.username = username;
  }
}
