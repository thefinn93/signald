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
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.util.Base64;

@ProtocolType("trust")
@Doc("Trust another user's safety number using either the QR code data or the safety number text")
public class TrustRequest implements RequestType<Empty> {
  private static final String FINGERPRINT_TYPE = "fingerprint-type";

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("The user to query identity keys for") @Required public JsonAddress address;

  @ExampleValue(ExampleValue.SAFETY_NUMBER)
  @ExactlyOneOfRequired(FINGERPRINT_TYPE)
  @JsonProperty("safety_number")
  @Doc("required if qr_code_data is absent")
  public String safetyNumber;

  @ExactlyOneOfRequired(FINGERPRINT_TYPE) @JsonProperty("qr_code_data") @Doc("base64-encoded QR code data. required if safety_number is absent") public String qrCodeData;

  @JsonProperty("trust_level")
  @ExampleValue("\"TRUSTED_VERIFIED\"")
  @Doc("One of TRUSTED_UNVERIFIED, TRUSTED_VERIFIED or UNTRUSTED. Default is TRUSTED_VERIFIED")
  public String trustLevel = "TRUSTED_VERIFIED";

  @Override
  public Empty run(Request request) throws InvalidRequestError, InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, FingerprintVersionMismatchError,
                                           InvalidBase64Error, UnknownIdentityKeyError, InvalidFingerprintError {
    TrustLevel level;
    try {
      level = TrustLevel.valueOf(trustLevel.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestError("invalid trust_level: " + trustLevel);
    }

    Manager m = Common.getManager(account);
    Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);

    boolean result;
    if (safetyNumber != null) {
      try {
        result = m.trustIdentitySafetyNumber(recipient, safetyNumber, level);
      } catch (SQLException | InvalidKeyException e) {
        throw new InternalError("error trusting safety number", e);
      }
    } else {
      byte[] rawQRdata;
      try {
        rawQRdata = Base64.decode(qrCodeData);
      } catch (IOException e) {
        throw new InvalidBase64Error();
      }
      try {
        result = m.trustIdentitySafetyNumber(recipient, rawQRdata, level);
      } catch (FingerprintVersionMismatchException e) {
        throw new FingerprintVersionMismatchError(e);
      } catch (FingerprintParsingException e) {
        throw new InvalidFingerprintError(e);
      } catch (SQLException | InvalidKeyException e) {
        throw new InternalError("error trusting new safety number", e);
      }
    }
    if (!result) {
      throw new UnknownIdentityKeyError();
    }
    return new Empty();
  }
}
