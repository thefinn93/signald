/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class SignalProfile {

  @JsonProperty private final String identityKey;

  @JsonProperty private final String name;

  private final File avatarFile;

  @JsonProperty private final String unidentifiedAccess;

  @JsonProperty private int unidentifiedAccessMode;

  @JsonProperty private final boolean unrestrictedUnidentifiedAccess;

  @JsonProperty private final Capabilities capabilities;

  @JsonProperty private final String about;

  @JsonProperty private final String emoji;

  @JsonProperty private final PaymentAddress paymentAddress;

  @JsonProperty private final List<SignalServiceProfile.Badge> badges;

  public SignalProfile(final SignalServiceProfile encryptedProfile, String name, String about, String aboutEmoji, final File avatarFile, final String unidentifiedAccess,
                       final SignalServiceProtos.PaymentAddress paymentAddress, List<SignalServiceProfile.Badge> badges) {
    this.identityKey = encryptedProfile.getIdentityKey();
    this.name = name;
    this.avatarFile = avatarFile;
    this.unidentifiedAccess = unidentifiedAccess;
    this.unrestrictedUnidentifiedAccess = encryptedProfile.isUnrestrictedUnidentifiedAccess();
    this.capabilities = new Capabilities(encryptedProfile.getCapabilities());
    this.about = about;
    this.emoji = aboutEmoji;
    if (paymentAddress != null) {
      this.paymentAddress = new PaymentAddress(paymentAddress);
    } else {
      this.paymentAddress = null;
    }
    if (badges != null) {
      this.badges = badges;
    } else {
      this.badges = new ArrayList<>();
    }
  }

  public SignalProfile(@JsonProperty("identityKey") final String identityKey, @JsonProperty("name") final String name,
                       @JsonProperty("unidentifiedAccess") final String unidentifiedAccess,
                       @JsonProperty("unrestrictedUnidentifiedAccess") final boolean unrestrictedUnidentifiedAccess, @JsonProperty("capabilities") final Capabilities capabilities,
                       @JsonProperty("about") final String about, @JsonProperty("emoji") final String emoji, @JsonProperty("paymentAddress") final PaymentAddress paymentAddress,
                       @JsonProperty("badge") List<SignalServiceProfile.Badge> badges) {
    this.identityKey = identityKey;
    this.name = name;
    this.avatarFile = null;
    this.unidentifiedAccess = unidentifiedAccess;
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    this.capabilities = capabilities;
    this.about = about;
    this.emoji = emoji;
    this.paymentAddress = paymentAddress;
    if (badges != null) {
      this.badges = badges;
    } else {
      this.badges = new ArrayList<>();
    }
  }

  public String getIdentityKey() { return identityKey; }

  public String getName() {
    if (name == null) {
      return "";
    }
    return name;
  }

  public String getAbout() {
    if (about == null) {
      return "";
    }
    return about;
  }

  public String getEmoji() {
    if (emoji == null) {
      return "";
    }
    return emoji;
  }

  public File getAvatarFile() { return avatarFile; }

  public String getUnidentifiedAccess() { return unidentifiedAccess; }

  public boolean isUnrestrictedUnidentifiedAccess() { return unrestrictedUnidentifiedAccess; }

  public Capabilities getCapabilities() { return capabilities; }

  @JsonIgnore
  public SignalServiceProtos.PaymentAddress getPaymentAddress() throws IOException {
    if (paymentAddress == null) {
      return null;
    }
    return paymentAddress.get();
  }

  @JsonIgnore
  public List<String> getVisibleBadgesIds() {
    return badges.stream().filter(SignalServiceProfile.Badge::isVisible).map(SignalServiceProfile.Badge::getId).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "SignalProfile{"
        + "identityKey='" + identityKey + '\'' + ", name='" + name + '\'' + ", avatarFile=" + avatarFile + ", unidentifiedAccess='" + unidentifiedAccess + '\'' +
        ", unrestrictedUnidentifiedAccess=" + unrestrictedUnidentifiedAccess + ", capabilities=" + capabilities + '}';
  }

  // Returns true if the name, avatar and capabilities are equal.
  public boolean matches(SignalProfile other) {
    if (!other.name.equals(name)) {
      return false;
    }

    if (other.avatarFile != null) {
      if (avatarFile == null) {
        // other has an avatar, we do not
        return false;
      }
      if (!other.avatarFile.getAbsolutePath().equals(avatarFile.getAbsolutePath())) {
        return false;
      }
    } else {
      if (avatarFile != null) {
        // other has no avatar, we do
        return false;
      }
    }

    if (!other.capabilities.equals(capabilities)) {
      return false;
    }

    return true;
  }

  public static class Capabilities {
    @JsonIgnore public boolean uuid;

    @JsonProperty public boolean gv2;

    @JsonProperty public boolean storage;

    @JsonProperty public boolean gv1Migration;

    @JsonProperty public boolean senderKey;

    @JsonProperty public boolean announcementGroup;

    @JsonProperty public boolean changeNumber;

    public Capabilities() {}

    public Capabilities(SignalServiceProfile.Capabilities capabilities) {
      gv1Migration = capabilities.isGv1Migration();
      gv2 = capabilities.isGv2();
      storage = capabilities.isStorage();
      senderKey = capabilities.isSenderKey();
      announcementGroup = capabilities.isAnnouncementGroup();
      changeNumber = capabilities.isChangeNumber();
    }

    public boolean equals(Capabilities other) {
      return other.uuid == uuid && other.gv2 == gv2 && other.storage == storage && other.gv1Migration == gv1Migration && other.senderKey == senderKey;
    }
  }
}
