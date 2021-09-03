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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.util.SafetyNumberHelper;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.whispersystems.libsignal.fingerprint.Fingerprint;

@Doc("a list of identity keys associated with a particular address")
public class IdentityKeyList {
  @JsonIgnore private final Recipient self;
  @JsonIgnore private final org.whispersystems.libsignal.IdentityKey ownKey;
  @JsonIgnore private final Recipient recipient;

  public final JsonAddress address;
  public final List<IdentityKey> identities = new ArrayList<>();

  public IdentityKeyList(Recipient self, org.whispersystems.libsignal.IdentityKey ownKey, Recipient recipient, List<IdentityKeysTable.IdentityKeyRow> identities)
      throws IOException, SQLException {
    this.self = self;
    this.ownKey = ownKey;
    this.address = new JsonAddress(recipient);
    this.recipient = recipient;
    if (identities == null) {
      return;
    }
    for (IdentityKeysTable.IdentityKeyRow i : identities) {
      addKey(i);
    }
  }

  public void addKey(IdentityKeysTable.IdentityKeyRow identity) throws IOException, SQLException {
    Fingerprint safetyNumber = SafetyNumberHelper.computeFingerprint(self, ownKey, recipient, identity.getKey());
    if (safetyNumber == null) {
      return;
    }
    identities.add(new IdentityKey(identity, safetyNumber));
  }
}
