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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.util.GroupsUtil;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GroupsV2Storage {
  public Map<Integer, JsonAuthCredential> credentials;
  public List<Group> groups;

  public GroupsV2Storage() {
    credentials = new HashMap<>();
    groups = new ArrayList<>();
  }

  public AuthCredentialResponse getAuthCredential(GroupsV2Api groupsV2Api, int today) throws IOException {
    if (!credentials.containsKey(today)) {
      credentials = groupsV2Api.getCredentials(today).entrySet().stream().map(JsonAuthCredential::fromMap).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    return credentials.get(today).credential;
  }

  public Group get(Group group) {
    String id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(group.getMasterKey()).serialize());
    return get(id);
  }

  public Group get(SignalServiceGroupV2 group) {
    String id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(group.getMasterKey()).serialize());
    return get(id);
  }

  public Group get(String id) {
    for (Group g : groups) {
      if (g.getID().equals(id)) {
        return g;
      }
    }
    return null;
  }

  public void update(JsonGroupV2Info groupInfo) {}

  public void update(Group group) {
    String id = group.getID();
    for (Group g : groups) {
      if (!g.getID().equals(id)) {
        continue;
      }
      g.update(group);
      return;
    }
    groups.add(group);
  }

  public void remove(Group group) {
    Group g = get(group);
    if (g == null) {
      return;
    }
    groups.remove(g);
  }

  @JsonSerialize(using = JsonAuthCredential.JsonAuthCredentialSerializer.class)
  @JsonDeserialize(using = JsonAuthCredential.JsonAuthCredentialDeserializer.class)
  static class JsonAuthCredential {
    AuthCredentialResponse credential;
    JsonAuthCredential(AuthCredentialResponse c) { credential = c; }

    public static Map.Entry<Integer, JsonAuthCredential> fromMap(Map.Entry<Integer, AuthCredentialResponse> e) {
      return new AbstractMap.SimpleEntry<>(e.getKey(), new JsonAuthCredential(e.getValue()));
    }

    public static class JsonAuthCredentialSerializer extends JsonSerializer<JsonAuthCredential> {
      @Override
      public void serialize(final JsonAuthCredential value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
        jgen.writeString(Base64.encodeBytes(value.credential.serialize()));
      }
    }

    public static class JsonAuthCredentialDeserializer extends JsonDeserializer<JsonAuthCredential> {
      @Override
      public JsonAuthCredential deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        AuthCredentialResponse c;
        try {
          c = new AuthCredentialResponse(Base64.decode(jsonParser.readValueAs(String.class)));
        } catch (InvalidInputException e) {
          e.printStackTrace();
          throw new IOException("failed to deserialize group auth credentials");
        }
        return new JsonAuthCredential(c);
      }
    }
  }
}
