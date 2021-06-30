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

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.File;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

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

  public SignalProfile(final SignalServiceProfile encryptedProfile, String name, String about, String aboutEmoji, final File avatarFile, final String unidentifiedAccess) {
    this.identityKey = encryptedProfile.getIdentityKey();
    this.name = name;
    this.avatarFile = avatarFile;
    this.unidentifiedAccess = unidentifiedAccess;
    this.unrestrictedUnidentifiedAccess = encryptedProfile.isUnrestrictedUnidentifiedAccess();
    this.capabilities = new Capabilities(encryptedProfile.getCapabilities());
    this.about = about;
    this.emoji = aboutEmoji;
  }

  public SignalProfile(@JsonProperty("identityKey") final String identityKey, @JsonProperty("name") final String name,
                       @JsonProperty("unidentifiedAccess") final String unidentifiedAccess,
                       @JsonProperty("unrestrictedUnidentifiedAccess") final boolean unrestrictedUnidentifiedAccess, @JsonProperty("capabilities") final Capabilities capabilities,
                       @JsonProperty("about") final String about, @JsonProperty("emoji") final String emoji) {
    this.identityKey = identityKey;
    this.name = name;
    this.avatarFile = null;
    this.unidentifiedAccess = unidentifiedAccess;
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    this.capabilities = capabilities;
    this.about = about;
    this.emoji = emoji;
  }

  public String getIdentityKey() { return identityKey; }

  public String getName() { return name; }

  public String getAbout() { return about; }

  public String getEmoji() { return emoji; }

  public File getAvatarFile() { return avatarFile; }

  public String getUnidentifiedAccess() { return unidentifiedAccess; }

  public boolean isUnrestrictedUnidentifiedAccess() { return unrestrictedUnidentifiedAccess; }

  public Capabilities getCapabilities() { return capabilities; }

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

    public Capabilities() {}

    public Capabilities(SignalServiceProfile.Capabilities capabilities) {
      gv1Migration = capabilities.isGv1Migration();
      gv2 = capabilities.isGv2();
      storage = capabilities.isStorage();
      senderKey = capabilities.isSenderKey();
    }

    public boolean equals(Capabilities other) {
      return other.uuid == uuid && other.gv2 == gv2 && other.storage == storage && other.gv1Migration == gv1Migration && other.senderKey == senderKey;
    }
  }
}
