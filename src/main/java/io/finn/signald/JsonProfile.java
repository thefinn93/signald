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

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.UUID;

public class JsonProfile {
  public String name;
  public String avatar;
  public String identity_key;
  public String unidentified_access;
  public boolean unrestricted_unidentified_access;
  public JsonCapabilities capabilities;
  public String uuid;

  @Doc("the address that signald used to look up this profile") public JsonAddress address;

  public JsonProfile(SignalServiceProfile p, ProfileKey profileKey, UUID address) throws IOException, InvalidInputException {
    this(p, profileKey.serialize(), new JsonAddress(address));
  }
  public JsonProfile(SignalServiceProfile p, byte[] profileKey, JsonAddress a) throws IOException, InvalidInputException {
    ProfileCipher profileCipher = new ProfileCipher(new ProfileKey(profileKey));
    if (p.getName() != null) {
      try {
        name = new String(profileCipher.decryptName(Base64.decode(p.getName())));
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
    if (p.getUuid() != null) {
      uuid = p.getUuid().toString();
    }
    address = a;
  }

  public class JsonCapabilities {
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
