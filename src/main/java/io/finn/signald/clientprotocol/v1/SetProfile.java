/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;
import io.finn.signald.Account;
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
import io.finn.signald.storage.SignalProfile;
import io.finn.signald.util.AttachmentUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

@ProtocolType("set_profile")
public class SetProfile implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The phone number of the account to use") @Required public String account;

  @ExampleValue("\"signald user\"") @Doc("Change the profile name") public String name;

  @ExampleValue(ExampleValue.LOCAL_EXTERNAL_JPG) @Doc("Path to new profile avatar file. If unset or null, unset the profile avatar") public String avatarFile;

  @Doc("Change the 'about' profile field") public String about;

  @Doc("Change the profile emoji") public String emoji;

  @Doc("Change the profile payment address. Payment address must be a *base64-encoded* MobileCoin address. Note that "
       + "this is not the traditional MobileCoin address encoding, which is custom. Clients are responsible for "
       + "converting between MobileCoin's custom base58 on the user-facing side and base64 encoding on the signald side.")
  @JsonProperty("mobilecoin_address")
  public String mobilecoinAddress;

  @Doc("configure visible badge IDs") @JsonProperty("visible_badge_ids") public List<String> visibleBadgeIds;

  private static final Logger logger = LogManager.getLogger();

  @Override
  public Empty run(Request request)
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidBase64Error, InvalidRequestError, AuthorizationFailedError {

    Manager m = Common.getManager(account);

    SignalProfile existing = m.getAccountData().profileCredentialStore.get(m.getOwnRecipient()).getProfile();

    File avatar = null;
    if (avatarFile != null) {
      avatar = new File(avatarFile);
    } else {
      File a = m.getProfileAvatarFile(m.getOwnRecipient());
      if (a.exists()) {
        avatar = a;
      }
    }

    if (name == null) {
      name = existing == null ? "" : existing.getName();
    }

    if (about == null) {
      about = existing == null ? "" : existing.getAbout();
    }

    if (emoji == null) {
      emoji = existing == null ? "" : existing.getEmoji();
    }

    Optional<SignalServiceProtos.PaymentAddress> paymentAddress;

    if (existing != null) {
      try {
        paymentAddress = Optional.fromNullable(existing.getPaymentAddress());
      } catch (IOException e) {
        logger.warn("error getting current payment address while setting profile: {}", e.getMessage());
        paymentAddress = Optional.absent();
      }
    } else {
      paymentAddress = Optional.absent();
    }

    if (mobilecoinAddress != null && !mobilecoinAddress.equals("")) {
      byte[] decodedAddress;
      try {
        decodedAddress = Base64.decode(mobilecoinAddress);
      } catch (IOException e) {
        throw new InvalidBase64Error();
      }
      Account a = Common.getAccount(account);
      IdentityKeyPair identityKeyPair = a.getProtocolStore().getIdentityKeyPair();
      SignalServiceProtos.PaymentAddress signedAddress = signPaymentsAddress(decodedAddress, identityKeyPair);

      SignalServiceProtos.PaymentAddress.Builder paymentAddressBuilder = SignalServiceProtos.PaymentAddress.newBuilder();
      paymentAddressBuilder.setMobileCoinAddress(signedAddress.getMobileCoinAddress());

      paymentAddress = Optional.of(paymentAddressBuilder.build());
    }

    if (visibleBadgeIds == null) {
      visibleBadgeIds = existing == null ? new ArrayList<>() : existing.getVisibleBadgesIds();
    }

    try (final StreamDetails streamDetails = avatar == null ? null : AttachmentUtil.createStreamDetailsFromFile(avatar)) {
      m.getAccountManager().setVersionedProfile(m.getACI(), m.getAccountData().getProfileKey(), name, about, emoji, paymentAddress, streamDetails, visibleBadgeIds);
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
