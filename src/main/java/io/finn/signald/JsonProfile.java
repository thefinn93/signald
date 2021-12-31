/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import java.io.IOException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonProfile {
  public String name;
  public String avatar;
  public String identity_key;
  public String unidentified_access;
  public boolean unrestricted_unidentified_access;
  public JsonCapabilities capabilities;
  public String uuid;

  @Doc("the address that signald used to look up this profile") public JsonAddress address;

  public JsonProfile(SignalServiceProfile p, ProfileKey profileKey, JsonAddress a) throws IOException {
    ProfileCipher profileCipher = new ProfileCipher(profileKey);
    if (p.getName() != null) {
      try {
        name = profileCipher.decryptString(Base64.decode(p.getName()));
      } catch (InvalidCiphertextException e) {
      }
    }
    identity_key = p.getIdentityKey();
    avatar = p.getAvatar();
    unidentified_access = p.getUnidentifiedAccess();
    if (p.isUnrestrictedUnidentifiedAccess()) {
      unrestricted_unidentified_access = true;
    }
    capabilities = new JsonCapabilities(p.getCapabilities());
    if (p.getAci() != null) {
      uuid = p.getAci().uuid().toString();
    }
    address = a;
  }

  public static class JsonCapabilities {
    public boolean gv2;
    public boolean storage;
    @JsonProperty("gv1-migration") public boolean gv1Migration;

    public JsonCapabilities(SignalServiceProfile.Capabilities c) {
      gv2 = c.isGv2();
      storage = c.isStorage();
      gv1Migration = c.isGv1Migration();
    }
  }
}
