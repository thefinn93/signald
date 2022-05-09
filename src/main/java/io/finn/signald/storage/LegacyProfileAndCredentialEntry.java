/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.IProfileKeysTable;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@Deprecated
@JsonDeserialize(using = LegacyProfileAndCredentialEntry.ProfileAndCredentialEntryDeserializer.class)
@JsonSerialize(using = LegacyProfileAndCredentialEntry.ProfileAndCredentialEntrySerializer.class)
public class LegacyProfileAndCredentialEntry {
  private static final Logger logger = LogManager.getLogger();
  private static final ObjectMapper mapper = JSONUtil.GetMapper();

  private static final byte[] UNRESTRICTED_KEY = new byte[16];

  private SignalServiceAddress serviceAddress;

  private final ProfileKey profileKey;

  private final long lastUpdateTimestamp;

  private final LegacySignalProfile profile;

  private final ProfileKeyCredential profileKeyCredential;

  private boolean requestPending;

  public LegacyProfileAndCredentialEntry(final SignalServiceAddress serviceAddress, final ProfileKey profileKey, final long lastUpdateTimestamp, final LegacySignalProfile profile,
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

  public LegacySignalProfile getProfile() { return profile; }

  public ProfileKeyCredential getProfileKeyCredential() { return profileKeyCredential; }

  public boolean isRequestPending() { return requestPending; }

  public void setRequestPending(final boolean requestPending) { this.requestPending = requestPending; }

  public void setAddress(SignalServiceAddress address) { serviceAddress = address; }

  public UnidentifiedAccessMode unidentifiedAccessMode;

  public byte[] getUnidentifiedAccessKey() {
    switch (unidentifiedAccessMode) {
    case UNKNOWN:
      if (profileKey == null) {
        return UNRESTRICTED_KEY;
      } else {
        return UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
      }
    case DISABLED:
      return null;
    case ENABLED:
      if (profileKey == null) {
        return null;
      } else {
        return UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
      }
    case UNRESTRICTED:
      return UNRESTRICTED_KEY;
    default:
      throw new AssertionError("Unknown mode: " + unidentifiedAccessMode);
    }
  }

  public UnidentifiedAccessMode getUnidentifiedAccessMode() { return unidentifiedAccessMode; }

  public static class ProfileAndCredentialEntryDeserializer extends JsonDeserializer<LegacyProfileAndCredentialEntry> {
    @Override
    public LegacyProfileAndCredentialEntry deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      JsonNode node = p.getCodec().readTree(p);

      SignalServiceAddress address = null;
      if (node.has("address")) {
        JsonAddress jsonAddress = mapper.treeToValue(node.get("address"), JsonAddress.class);
        if (jsonAddress.uuid != null) {
          address = jsonAddress.getSignalServiceAddress();
        } else {
          logger.debug("dropping known profile due to lack of known UUID: " + jsonAddress.toRedactedString());
        }
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

      LegacySignalProfile profile = null;
      if (node.has("profile")) {
        profile = mapper.treeToValue(node.get("profile"), LegacySignalProfile.class);
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

      return new LegacyProfileAndCredentialEntry(address, profileKey, lastUpdateTimestamp, profile, profileKeyCredential, unidentifiedAccessMode);
    }
  }

  public static class ProfileAndCredentialEntrySerializer extends JsonSerializer<LegacyProfileAndCredentialEntry> {
    @Override
    public void serialize(LegacyProfileAndCredentialEntry value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      if (value.serviceAddress != null) {
        node.set("address", mapper.valueToTree(new JsonAddress(value.serviceAddress)));
      } else {
        return; // don't store profiles without an address
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

    public IProfileKeysTable.UnidentifiedAccessMode migrate() { return IProfileKeysTable.UnidentifiedAccessMode.fromMode(getMode()); }
  }
}
