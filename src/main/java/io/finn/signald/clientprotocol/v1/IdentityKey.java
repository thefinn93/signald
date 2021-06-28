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
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.db.IdentityKeysTable;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.util.Base64;

public class IdentityKey {
  @ExampleValue(ExampleValue.SAFETY_NUMBER) @JsonProperty("safety_number") public String safetyNumber;
  @JsonProperty("qr_code_data") @Doc("base64-encoded QR code data") public String qrCodeData;
  @JsonProperty("trust_level") @Doc("One of TRUSTED_UNVERIFIED, TRUSTED_VERIFIED or UNTRUSTED") public String trustLevel;
  @Doc("the first time this identity key was seen") public long added;

  public IdentityKey(IdentityKeysTable.IdentityKeyRow identity, Fingerprint fingerprint) {
    trustLevel = identity.getTrustLevelString();
    added = identity.getAddedTimestamp();
    safetyNumber = fingerprint.getDisplayableFingerprint().getDisplayText();
    qrCodeData = Base64.encodeBytes(fingerprint.getScannableFingerprint().getSerialized());
  }
}
