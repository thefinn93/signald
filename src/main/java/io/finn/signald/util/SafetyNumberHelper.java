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

package io.finn.signald.util;

import io.finn.signald.db.Recipient;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class SafetyNumberHelper {
  // It seems like the official Signal apps don't use v2 safety numbers yet, so this is disabled for now.
  public static boolean UseV2SafetyNumbers = false;

  // computeSafetyNumber derived from signal-cli (computeSafetyNumber in src/main/java/org/asamk/signal/manager/Common.java)
  public static Fingerprint computeFingerprint(Recipient self, IdentityKey ownIdentityKey, Recipient recipient, IdentityKey theirIdentityKey) {
    int version;
    byte[] ownId;
    byte[] theirId;

    if (UseV2SafetyNumbers && self.getUUID() != null && recipient.getUUID() != null) {
      // Version 2: UUID user
      version = 2;
      ownId = UuidUtil.toByteArray(self.getUUID());
      theirId = UuidUtil.toByteArray(recipient.getUUID());
    } else {
      // Version 1: E164 user
      version = 1;
      if (!self.getAddress().getNumber().isPresent() || !recipient.getAddress().getNumber().isPresent()) {
        return null;
      }
      ownId = self.getAddress().getNumber().get().getBytes();
      theirId = recipient.getAddress().getNumber().get().getBytes();
    }

    return new NumericFingerprintGenerator(5200).createFor(version, ownId, ownIdentityKey, theirId, theirIdentityKey);
  }

  public static String computeSafetyNumber(Recipient self, IdentityKey ownIdentityKey, Recipient recipient, IdentityKey theirIdentityKey) {
    Fingerprint fingerprint = computeFingerprint(self, ownIdentityKey, recipient, theirIdentityKey);
    if (fingerprint == null) {
      return "INVALID ID";
    }
    return fingerprint.getDisplayableFingerprint().getDisplayText();
  }
}
