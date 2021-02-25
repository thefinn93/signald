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
import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.NoSuchAccountException;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.exceptions.InvalidAddressException;
import io.finn.signald.exceptions.InvalidRequestException;
import io.finn.signald.exceptions.UnknownIdentityKey;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.sql.SQLException;

@SignaldClientRequest(type = "trust")
@Doc("Trust another user's safety number using either the QR code data or the safety number text")
public class TrustRequest implements RequestType<Empty> {
  private static final String FINGERPRINT_TYPE = "fingerprint-type";

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("The user to query identity keys for") @Required public JsonAddress address;

  @ExactlyOneOfRequired(FINGERPRINT_TYPE) @JsonProperty("safety_number") @Doc("required if qr_code_data is absent") public String safetyNumber;

  @ExactlyOneOfRequired(FINGERPRINT_TYPE) @JsonProperty("qr_code_data") @Doc("base64-encoded QR code data. required if safety_number is absent") public String qrCodeData;

  @Required @JsonProperty("trust_level") @ExampleValue("\"TRUSTED_VERIFIED\"") @Doc("One of TRUSTED_UNVERIFIED, TRUSTED_VERIFIED or UNTRUSTED") public String trustLevel;

  @Override
  public Empty run(Request request) throws SQLException, IOException, NoSuchAccountException, InvalidRequestException, FingerprintVersionMismatchException,
                                           FingerprintParsingException, UnknownIdentityKey, InvalidAddressException, InvalidKeyException {
    TrustLevel level;
    try {
      level = TrustLevel.valueOf(trustLevel.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("invalid trust_level: " + trustLevel);
    }

    Manager m = Manager.get(account);

    SignalServiceAddress addr = m.getResolver().resolve(address.getSignalServiceAddress());

    boolean result;
    if (safetyNumber != null) {
      result = m.trustIdentitySafetyNumber(addr, safetyNumber, level);
    } else {
      result = m.trustIdentitySafetyNumber(addr, Base64.decode(qrCodeData), level);
    }
    if (!result) {
      throw new UnknownIdentityKey();
    }
    return new Empty();
  }
}
