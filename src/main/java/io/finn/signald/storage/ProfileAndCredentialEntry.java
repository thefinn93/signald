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

package io.finn.signald.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.finn.signald.Util;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;

@JsonDeserialize(using = ProfileAndCredentialEntry.ProfileAndCredentialEntryDeserializer.class)
@JsonSerialize(using = ProfileAndCredentialEntry.ProfileAndCredentialEntrySerializer.class)
public class ProfileAndCredentialEntry {
  private static final Logger logger = LogManager.getLogger();
  private static final ObjectMapper mapper = JSONUtil.GetMapper();

  private SignalServiceAddress serviceAddress;

  private final ProfileKey profileKey;

  private final long lastUpdateTimestamp;

  private final SignalProfile profile;

  private final ProfileKeyCredential profileKeyCredential;

  private boolean requestPending;

  public ProfileAndCredentialEntry(final SignalServiceAddress serviceAddress, final ProfileKey profileKey, final long lastUpdateTimestamp, final SignalProfile profile,
                                   final ProfileKeyCredential profileKeyCredential, UnidentifiedAccessMode unidentifiedAccessMode) {
    this.serviceAddress = serviceAddress;
    this.profileKey = profileKey;
    this.lastUpdateTimestamp = lastUpdateTimestamp;
    this.profile = profile;
    this.profileKeyCredential = profileKeyCredential;
    this.unidentifiedAccessMode = unidentifiedAccessMode;
  }

  public SignalServiceAddress getServiceAddress() { return serviceAddress; }

  public ProfileKey getProfileKey() { return profileKey; }

  public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }

  public SignalProfile getProfile() { return profile; }

  public ProfileKeyCredential getProfileKeyCredential() { return profileKeyCredential; }

  public boolean isRequestPending() { return requestPending; }

  public void setRequestPending(final boolean requestPending) { this.requestPending = requestPending; }

  public void setAddress(SignalServiceAddress address) { serviceAddress = address; }

  public UnidentifiedAccessMode unidentifiedAccessMode;

  public byte[] getUnidentifiedAccessKey() {
    if (profile == null) {
      return null;
    }
    if (profile.isUnrestrictedUnidentifiedAccess()) {
      return Util.getSecretBytes(16);
    }

    return UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
  }

  public UnidentifiedAccessMode getUnidentifiedAccessMode() { return unidentifiedAccessMode; }

  public static class ProfileAndCredentialEntryDeserializer extends JsonDeserializer<ProfileAndCredentialEntry> {
    @Override
    public ProfileAndCredentialEntry deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      JsonNode node = p.getCodec().readTree(p);

      SignalServiceAddress address = null;
      if (node.has("address")) {
        address = mapper.treeToValue(node.get("address"), JsonAddress.class).getSignalServiceAddress();
      }

      ProfileKey profileKey = null;
      if (node.has("profileKey")) {
        try {
          profileKey = new ProfileKey(Base64.decode(node.get("profileKey").textValue()));
        } catch (InvalidInputException e) {
          logger.warn("error loading profile key from profile credential storage");
        }
      }

      long lastUpdateTimestamp = node.get("lastUpdateTimestamp").asLong();

      SignalProfile profile = null;
      if (node.has("profile")) {
        profile = mapper.treeToValue(node.get("profile"), SignalProfile.class);
      }

      ProfileKeyCredential profileKeyCredential = null;
      if (node.has("profileKeyCredential")) {
        try {
          profileKeyCredential = new ProfileKeyCredential(Base64.decode(node.get("profileKeyCredential").textValue()));
        } catch (InvalidInputException e) {
          logger.warn("error loading profile key credential from profile credential storage");
        }
      }

      UnidentifiedAccessMode unidentifiedAccessMode = UnidentifiedAccessMode.UNKNOWN;
      if (node.has("unidentifiedAccessMode")) {
        unidentifiedAccessMode = UnidentifiedAccessMode.fromMode(node.get("unidentifiedAccessMode").asInt());
      }

      return new ProfileAndCredentialEntry(address, profileKey, lastUpdateTimestamp, profile, profileKeyCredential, unidentifiedAccessMode);
    }
  }

  public static class ProfileAndCredentialEntrySerializer extends JsonSerializer<ProfileAndCredentialEntry> {
    @Override
    public void serialize(ProfileAndCredentialEntry value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      if (value.serviceAddress != null) {
        node.set("address", mapper.valueToTree(new JsonAddress(value.serviceAddress)));
      }

      if (value.profileKey != null) {
        node.put("profileKey", Base64.encodeBytes(value.profileKey.serialize()));
      }

      node.put("lastUpdateTimestamp", value.lastUpdateTimestamp);

      if (value.profile != null) {
        node.set("profile", mapper.valueToTree(value.profile));
      }

      if (value.profileKeyCredential != null) {
        node.put("profileKeyCredential", Base64.encodeBytes(value.profileKeyCredential.serialize()));
      }

      node.put("unidentifiedAccessMode", value.unidentifiedAccessMode.getMode());

      gen.writeObject(node);
    }
  }

  public enum UnidentifiedAccessMode {
    UNKNOWN(0),
    DISABLED(1),
    ENABLED(2),
    UNRESTRICTED(3);

    private final int mode;

    UnidentifiedAccessMode(int mode) { this.mode = mode; }

    public int getMode() { return mode; }

    public static UnidentifiedAccessMode fromMode(int mode) { return values()[mode]; }
  }
}
