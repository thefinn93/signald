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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.storage.IdentityKeyStore;
import io.finn.signald.util.SafetyNumberHelper;
import org.asamk.signal.util.Hex;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

class JsonIdentity {
  public String trust_level;
  public long added;
  public String fingerprint;
  public String safety_number;
  public String qr_code_data;
  public JsonAddress address;

  JsonIdentity(IdentityKeyStore.Identity identity, Manager m) {
    this.trust_level = identity.getTrustLevel().name();
    this.added = identity.getDateAdded().getTime();
    this.fingerprint = Hex.toStringCondensed(identity.getFingerprint());
    if(identity.getAddress() != null) {
      this.address = identity.getAddress();
      generateSafetyNumber(identity, m);
    }
  }

  JsonIdentity(IdentityKeyStore.Identity identity, Manager m, SignalServiceAddress address) {
    this(identity, m);
    this.address = new JsonAddress(address);
    generateSafetyNumber(identity, m);
  }

  private void generateSafetyNumber(IdentityKeyStore.Identity identity, Manager m) {
    if(address != null) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(m.getOwnAddress(), m.getIdentity(), address.getSignalServiceAddress(), identity.getKey());
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
    if(this.address == null) {
      this.address = new JsonAddress(number);
    } else {
      this.address.number = number;
    }
  }
}
