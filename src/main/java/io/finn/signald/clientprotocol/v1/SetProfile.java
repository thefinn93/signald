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
import com.google.protobuf.ByteString;
import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.util.AttachmentUtil;
import java.io.File;
import java.io.IOException;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

@ProtocolType("set_profile")
public class SetProfile implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The phone number of the account to use") @Required public String account;

  @ExampleValue("\"signald user\"") @Doc("New profile name. Set to empty string for no profile name") @Required public String name;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) @Doc("Path to new profile avatar file. If unset or null, unset the profile avatar") public String avatarFile;

  @Doc("an optional about string. If unset, null or an empty string will unset profile about field") public String about;

  @Doc("an optional single emoji character. If unset, null or an empty string will unset profile emoji") public String emoji;

  @Doc("an optional *base64-encoded* MobileCoin address to set in the profile. Note that this is not the traditional "
       + "MobileCoin address encoding, which is custom. Clients are responsible for converting between MobileCoin's "
       + "custom base58 on the user-facing side and base64 encoding on the signald side. If unset, null or an empty "
       + "string, will empty the profile payment address")
  @JsonProperty("mobilecoin_address")
  public String mobilecoinAddress;

  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidBase64Error {
    File avatar = avatarFile == null ? null : new File(avatarFile);

    Manager m = Common.getManager(account);

    if (name == null) {
      name = "";
    }

    if (about == null) {
      about = "";
    }

    if (emoji == null) {
      emoji = "";
    }

    Optional<SignalServiceProtos.PaymentAddress> paymentAddress = Optional.absent();

    if (mobilecoinAddress != null && !mobilecoinAddress.equals("")) {
      byte[] decodedAddress = new byte[0];
      try {
        decodedAddress = Base64.decode(mobilecoinAddress);
      } catch (IOException e) {
        throw new InvalidBase64Error();
      }
      IdentityKeyPair identityKeyPair = m.getAccountData().axolotlStore.getIdentityKeyPair();
      SignalServiceProtos.PaymentAddress signedAddress = signPaymentsAddress(decodedAddress, identityKeyPair);

      SignalServiceProtos.PaymentAddress.Builder paymentAddressBuilder = SignalServiceProtos.PaymentAddress.newBuilder();
      paymentAddressBuilder.setMobileCoinAddress(signedAddress.getMobileCoinAddress());

      paymentAddress = Optional.of(paymentAddressBuilder.build());
    }

    try (final StreamDetails streamDetails = avatar == null ? null : AttachmentUtil.createStreamDetailsFromFile(avatar)) {
      m.getAccountManager().setVersionedProfile(m.getUUID(), m.getAccountData().getProfileKey(), name, about, emoji, paymentAddress, streamDetails);
    } catch (IOException e) {
      throw new InternalError("error reading avatar file", e);
    } catch (InvalidInputException e) {
      throw new InternalError("error getting own profile key", e);
    }

    return new Empty();
  }

  static SignalServiceProtos.PaymentAddress signPaymentsAddress(byte[] publicAddressBytes, IdentityKeyPair identityKeyPair) {
    byte[] signature = identityKeyPair.getPrivateKey().calculateSignature(publicAddressBytes);

    return SignalServiceProtos.PaymentAddress.newBuilder()
        .setMobileCoinAddress(
            SignalServiceProtos.PaymentAddress.MobileCoinAddress.newBuilder().setAddress(ByteString.copyFrom(publicAddressBytes)).setSignature(ByteString.copyFrom(signature)))
        .build();
  }
}
