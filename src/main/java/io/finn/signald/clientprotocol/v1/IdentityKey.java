/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.db.IIdentityKeysTable;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.whispersystems.util.Base64;

public class IdentityKey {
  @ExampleValue(ExampleValue.SAFETY_NUMBER) @JsonProperty("safety_number") public String safetyNumber;
  @JsonProperty("qr_code_data") @Doc("base64-encoded QR code data") public String qrCodeData;
  @JsonProperty("trust_level") @Doc("One of TRUSTED_UNVERIFIED, TRUSTED_VERIFIED or UNTRUSTED") public String trustLevel;
  @Doc("the first time this identity key was seen") public long added;

  public IdentityKey(IIdentityKeysTable.IdentityKeyRow identity, Fingerprint fingerprint) {
    trustLevel = identity.getTrustLevelString();
    added = identity.getAddedTimestamp();
    safetyNumber = fingerprint.getDisplayableFingerprint().getDisplayText();
    qrCodeData = Base64.encodeBytes(fingerprint.getScannableFingerprint().getSerialized());
  }

  public IdentityKey(String trustLevel, Fingerprint fingerprint) {
    this.trustLevel = trustLevel;
    safetyNumber = fingerprint.getDisplayableFingerprint().getDisplayText();
    qrCodeData = Base64.encodeBytes(fingerprint.getScannableFingerprint().getSerialized());
  }
}
