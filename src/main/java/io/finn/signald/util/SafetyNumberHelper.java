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

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SafetyNumberHelper {

    // computeSafetyNumber derived from signal-cli (computeSafetyNumber in src/main/java/org/asamk/signal/manager/Utils.java)
    public static String computeSafetyNumber(SignalServiceAddress ownAddress, IdentityKey ownIdentityKey, SignalServiceAddress theirAddress, IdentityKey theirIdentityKey) {
        int version;
        byte[] ownId;
        byte[] theirId;

//        if (ServiceConfig.capabilities.isUuid() && ownAddress.getUuid().isPresent() && theirAddress.getUuid().isPresent()) {
//            // Version 2: UUID user
//            version = 2;
//            ownId = UuidUtil.toByteArray(ownAddress.getUuid().get());
//            theirId = UuidUtil.toByteArray(theirAddress.getUuid().get());
//        } else {
        // Version 1: E164 user
        version = 1;
        if (!ownAddress.getNumber().isPresent() || !theirAddress.getNumber().isPresent()) {
            return "INVALID ID";
        }
        ownId = ownAddress.getNumber().get().getBytes();
        theirId = theirAddress.getNumber().get().getBytes();
//        }

        Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(version, ownId, ownIdentityKey, theirId, theirIdentityKey);
        return fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public static String computeSafetyNumber(String ownAddress, IdentityKey ownIdentityKey, String theirAddress, IdentityKey theirIdentityKey) {
        return computeSafetyNumber(new SignalServiceAddress(null, ownAddress), ownIdentityKey, new SignalServiceAddress(null, theirAddress), theirIdentityKey);
    }
}
