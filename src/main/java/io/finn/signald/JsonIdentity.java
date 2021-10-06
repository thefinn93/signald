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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.IdentityKeysTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.util.SafetyNumberHelper;
import java.io.IOException;
import java.sql.SQLException;
import org.asamk.signal.util.Hex;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
class JsonIdentity {
  public String trust_level;
  public long added;
  public String fingerprint;
  public String safety_number;
  public String qr_code_data;
  public JsonAddress address;

  JsonIdentity(IdentityKeysTable.IdentityKeyRow identity, Manager m) throws IOException, SQLException {
    this.trust_level = identity.getTrustLevel().name();
    this.added = identity.getDateAdded().getTime();
    this.fingerprint = Hex.toStringCondensed(identity.getFingerprint());
    if (identity.getAddress() != null) {
      this.address = new JsonAddress(identity.getAddress());
      generateSafetyNumber(identity, m);
    }
  }

  JsonIdentity(IdentityKeysTable.IdentityKeyRow identity, Manager m, Recipient recipient) throws IOException, SQLException {
    this(identity, m);
    this.address = new JsonAddress(recipient.getAddress());
    generateSafetyNumber(identity, m);
  }

  private void generateSafetyNumber(IdentityKeysTable.IdentityKeyRow identity, Manager m) throws IOException, SQLException {
    if (address != null) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(m.getOwnRecipient(), m.getIdentity(), m.getRecipientsTable().get(address), identity.getKey());
      if (fingerprint == null) {
        safety_number = "INVALID ID";
      } else {
        safety_number = fingerprint.getDisplayableFingerprint().getDisplayText();
        qr_code_data = Base64.encodeBytes(fingerprint.getScannableFingerprint().getSerialized());
      }
    }
  }

  @JsonProperty
  public void setNumber(String number) {
    if (this.address == null) {
      this.address = new JsonAddress(number);
    } else {
      this.address.number = number;
    }
  }
}
