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
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.*;


@JsonSerialize(using=IdentityKeyStore.IdentityKeyStoreSerializer.class)
@JsonDeserialize(using=IdentityKeyStore.IdentityKeyStoreDeserializer.class)
public class IdentityKeyStore implements org.whispersystems.libsignal.state.IdentityKeyStore {

    Map<String, List<IdentityKeyStore.Identity>> trustedKeys = new HashMap<>();

    IdentityKeyPair identityKeyPair;
    int registrationId;

    public IdentityKeyStore() {}

    public IdentityKeyStore(IdentityKeyPair identityKeyPair, int localRegistrationId) {
        this.identityKeyPair = identityKeyPair;
        this.registrationId = localRegistrationId;
    }

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        return identityKeyPair;
    }

    @Override
    public int getLocalRegistrationId() {
        return registrationId;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        return saveIdentity(address.getName(), identityKey, TrustLevel.TRUSTED_UNVERIFIED, null);
    }

    public boolean saveIdentity(String address, IdentityKey identityKey, TrustLevel trustLevel) {
        return saveIdentity(address, identityKey, trustLevel, null);
    }

    /**
     * Adds or updates the given identityKey for the user name and sets the trustLevel and added timestamp.
     *
     * @param name        User name, i.e. phone number
     * @param identityKey The user's public key
     * @param trustLevel
     * @param added       Added timestamp, if null and the key is newly added, the current time is used.
     */
    public boolean saveIdentity(String name, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
        List<IdentityKeyStore.Identity> identities = trustedKeys.get(name);
        if (identities == null) {
            identities = new ArrayList<>();
            trustedKeys.put(name, identities);
        } else {
            for (IdentityKeyStore.Identity id : identities) {
                if (!id.identityKey.equals(identityKey))
                    continue;

                if (id.trustLevel.compareTo(trustLevel) < 0) {
                    id.trustLevel = trustLevel;
                }
                if (added != null) {
                    id.added = added;
                }
                return true;
            }
        }
        identities.add(new IdentityKeyStore.Identity(identityKey, trustLevel, added != null ? added : new Date()));
        return false;
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        // TODO implement possibility for different handling of incoming/outgoing trust decisions
        List<IdentityKeyStore.Identity> identities = trustedKeys.get(address.getName());
        if (identities == null) {
            // Trust on first use
            return true;
        }

        for (IdentityKeyStore.Identity id : identities) {
            if (id.identityKey.equals(identityKey)) {
                return id.isTrusted();
            }
        }

        return false;
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        List<IdentityKeyStore.Identity> identities = trustedKeys.get(address.getName());
        if (identities == null || identities.size() == 0) {
            return null;
        }

        long maxDate = 0;
        IdentityKeyStore.Identity maxIdentity = null;
        for (IdentityKeyStore.Identity id : identities) {
            final long time = id.getDateAdded().getTime();
            if (maxIdentity == null || maxDate <= time) {
                maxDate = time;
                maxIdentity = id;
            }
        }
        return maxIdentity.getIdentityKey();
    }

    public Map<String, List<IdentityKeyStore.Identity>> getIdentities() {
        // TODO deep copy
        return trustedKeys;
    }

    public List<IdentityKeyStore.Identity> getIdentities(String name) {
        // TODO deep copy
        return trustedKeys.get(name);
    }

    public List<IdentityKeyStore.Identity> getIdentities(SignalServiceAddress address) {
        // TODO deep copy
        return trustedKeys.get(address.getNumber().get());
    }

    public static class IdentityKeyStoreDeserializer extends JsonDeserializer<IdentityKeyStore> {

        @Override
        public IdentityKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);

            try {
                int localRegistrationId = node.get("registrationId").asInt();
                IdentityKeyPair identityKeyPair = new IdentityKeyPair(org.whispersystems.util.Base64.decode(node.get("identityKey").asText()));

                IdentityKeyStore keyStore = new IdentityKeyStore(identityKeyPair, localRegistrationId);

                JsonNode trustedKeysNode = node.get("trustedKeys");
                if (trustedKeysNode.isArray()) {
                    for (JsonNode trustedKey : trustedKeysNode) {
                        String trustedKeyName = trustedKey.get("name").asText();
                        try {
                            IdentityKey id = new IdentityKey(org.whispersystems.util.Base64.decode(trustedKey.get("identityKey").asText()), 0);
                            TrustLevel trustLevel = trustedKey.has("trustLevel") ? TrustLevel.fromInt(trustedKey.get("trustLevel").asInt()) : TrustLevel.TRUSTED_UNVERIFIED;
                            Date added = trustedKey.has("addedTimestamp") ? new Date(trustedKey.get("addedTimestamp").asLong()) : new Date();
                            keyStore.saveIdentity(trustedKeyName, id, trustLevel, added);
                        } catch (InvalidKeyException | IOException e) {
                            System.out.println(String.format("Error while decoding key for: %s", trustedKeyName));
                        }
                    }
                }

                return keyStore;
            } catch (InvalidKeyException e) {
                throw new IOException(e);
            }
        }
    }

    public static class IdentityKeyStoreSerializer extends JsonSerializer<IdentityKeyStore> {

        @Override
        public void serialize(IdentityKeyStore jsonIdentityKeyStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartObject();
            json.writeNumberField("registrationId", jsonIdentityKeyStore.getLocalRegistrationId());
            json.writeStringField("identityKey", org.whispersystems.util.Base64.encodeBytes(jsonIdentityKeyStore.getIdentityKeyPair().serialize()));
            json.writeArrayFieldStart("trustedKeys");
            for (Map.Entry<String, List<IdentityKeyStore.Identity>> trustedKey : jsonIdentityKeyStore.trustedKeys.entrySet()) {
                for (IdentityKeyStore.Identity id : trustedKey.getValue()) {
                    json.writeStartObject();
                    json.writeStringField("name", trustedKey.getKey());
                    json.writeStringField("identityKey", Base64.encodeBytes(id.identityKey.serialize()));
                    json.writeNumberField("trustLevel", id.trustLevel.ordinal());
                    json.writeNumberField("addedTimestamp", id.added.getTime());
                    json.writeEndObject();
                }
            }
            json.writeEndArray();
            json.writeEndObject();
        }
    }

    public class Identity {

        IdentityKey identityKey;
        TrustLevel trustLevel;
        Date added;

        public Identity(IdentityKey identityKey, TrustLevel trustLevel) {
            this.identityKey = identityKey;
            this.trustLevel = trustLevel;
            this.added = new Date();
        }

        Identity(IdentityKey identityKey, TrustLevel trustLevel, Date added) {
            this.identityKey = identityKey;
            this.trustLevel = trustLevel;
            this.added = added;
        }

        boolean isTrusted() {
            return trustLevel == TrustLevel.TRUSTED_UNVERIFIED ||
                    trustLevel == TrustLevel.TRUSTED_VERIFIED;
        }

        public IdentityKey getIdentityKey() {
            return this.identityKey;
        }

        public TrustLevel getTrustLevel() {
            return this.trustLevel;
        }

        public Date getDateAdded() {
            return this.added;
        }

        public byte[] getFingerprint() {
            return identityKey.getPublicKey().serialize();
        }
    }
}
