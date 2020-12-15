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
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@JsonDeserialize(using = ProfileCredentialStore.ProfileCredentialStoreDeserializer.class)
@JsonSerialize(using = ProfileCredentialStore.ProfileCredentialStoreSerializer.class)
public class ProfileCredentialStore {
  Map<UUID, ProfileKeyCredential> credential = new HashMap<>();
  Map<UUID, SignalServiceProfile> profiles;

  public ProfileKeyCredential getCredential(UUID address, ProfileKey profileKey, SignalServiceMessageReceiver messageReceiver)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (!credential.containsKey(address)) {
      Optional<ProfileKey> profileKeyOptional = Optional.of(profileKey);
      SignalServiceAddress signalServiceAddress = new SignalServiceAddress(Optional.of(address), Optional.absent());
      SignalServiceProfile.RequestType requestType = SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL;
      ProfileAndCredential profileAndCredential =
          messageReceiver.retrieveProfile(signalServiceAddress, profileKeyOptional, Optional.absent(), requestType).get(10, TimeUnit.SECONDS);
      profiles.put(address, profileAndCredential.getProfile());
      if (profileAndCredential.getProfileKeyCredential().isPresent()) {
        credential.put(address, profileAndCredential.getProfileKeyCredential().get());
      }
    }
    return credential.get(address);
  }

  public static class ProfileCredentialStoreDeserializer extends JsonDeserializer<ProfileCredentialStore> {
    @Override
    public ProfileCredentialStore deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      ProfileCredentialStore profileCredentialStore = new ProfileCredentialStore();
      for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> profileNode = it.next();
        UUID uuid = UUID.fromString(profileNode.getKey());
        try {
          ProfileKeyCredential profileKeyCredential = new ProfileKeyCredential(Base64.decode(profileNode.getValue().textValue()));
          profileCredentialStore.credential.put(uuid, profileKeyCredential);
        } catch (InvalidInputException e) {
          e.printStackTrace();
        }
      }
      return profileCredentialStore;
    }
  }

  public static class ProfileCredentialStoreSerializer extends JsonSerializer<ProfileCredentialStore> {
    @Override
    public void serialize(ProfileCredentialStore value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
      for (Map.Entry<UUID, ProfileKeyCredential> entry : value.credential.entrySet()) {
        objectNode.put(entry.getKey().toString(), Base64.encodeBytes(entry.getValue().serialize()));
      }
      gen.writeObject(objectNode);
    }
  }
}
