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
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.IProfilesTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.AttachmentUtil;
import io.finn.signald.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
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
      throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidBase64Error, InvalidRequestError, AuthorizationFailedError, SQLError {
    Account a = Common.getAccount(account);

    Recipient self;
    try {
      self = a.getSelf();
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (IOException e) {
      throw new InternalError("unexpected error getting own recipient", e);
    }

    IProfilesTable.Profile existing;
    try {
      existing = a.getDB().ProfilesTable.get(self);
    } catch (SQLException e) {
      throw new SQLError(e);
    }

    String avatar = null;
    if (avatarFile != null) {
      avatar = new File(avatarFile).getAbsolutePath();
    } else {
      avatar = FileUtil.getProfileAvatarPath(self);
    }

    if (name == null) {
      name = existing == null ? "" : existing.getSerializedFullName();
    }

    if (about == null) {
      about = existing == null ? "" : existing.getAbout();
    }

    if (emoji == null) {
      emoji = existing == null ? "" : existing.getEmoji();
    }

    Optional<SignalServiceProtos.PaymentAddress> paymentAddress;
    if (existing != null) {
      paymentAddress = Optional.ofNullable(existing.getPaymentAddress());
    } else {
      paymentAddress = Optional.empty();
    }

    if (mobilecoinAddress != null && !mobilecoinAddress.equals("")) {
      byte[] decodedAddress;
      try {
        decodedAddress = Base64.decode(mobilecoinAddress);
      } catch (IOException e) {
        throw new InvalidBase64Error();
      }
      IdentityKeyPair identityKeyPair = a.getProtocolStore().getIdentityKeyPair();
      SignalServiceProtos.PaymentAddress signedAddress = signPaymentsAddress(decodedAddress, identityKeyPair);

      SignalServiceProtos.PaymentAddress.Builder paymentAddressBuilder = SignalServiceProtos.PaymentAddress.newBuilder();
      paymentAddressBuilder.setMobileCoinAddress(signedAddress.getMobileCoinAddress());

      paymentAddress = Optional.of(paymentAddressBuilder.build());
    }

    if (visibleBadgeIds == null) {
      visibleBadgeIds = existing == null ? new ArrayList<>() : IProfilesTable.StoredBadge.getVisibleIds(existing.getBadges());
    }

    ProfileKey ownProfileKey;
    try {
      ownProfileKey = a.getDB().ProfileKeysTable.getProfileKey(a.getSelf());
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (IOException e) {
      throw new InternalError("unexpected error getting own profile key: ", e);
    }

    try (final StreamDetails streamDetails = avatar == null ? null : AttachmentUtil.createStreamDetailsFromFile(new File(avatar))) {
      AvatarUploadParams avatarUploadParams = AvatarUploadParams.forAvatar(streamDetails);
      a.getSignalDependencies().getAccountManager().setVersionedProfile(a.getACI(), ownProfileKey, name, about, emoji, paymentAddress, avatarUploadParams, visibleBadgeIds);
    } catch (IOException e) {
      throw new InternalError("error reading avatar file", e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
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
