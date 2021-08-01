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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.util.Base64;

@JsonSerialize(using = GroupStore.GroupStoreSerializer.class)
@JsonDeserialize(using = GroupStore.GroupStoreDeserializer.class)
public class GroupStore {
  static final Logger logger = LogManager.getLogger();

  private static final ObjectMapper jsonProcessor = new ObjectMapper();

  private Map<String, GroupInfo> groups = new HashMap<>();

  public void updateGroup(GroupInfo group) { groups.put(Base64.encodeBytes(group.groupId), group); }

  public GroupInfo getGroup(String groupId) { return groups.get(groupId); }

  public GroupInfo getGroup(byte[] groupId) { return getGroup(Base64.encodeBytes(groupId)); }

  public List<GroupInfo> getGroups() { return new ArrayList<>(groups.values()); }

  public boolean deleteGroup(String groupId) { return groups.remove(groupId) == null; }

  public static class GroupStoreSerializer extends JsonSerializer<GroupStore> {
    @Override
    public void serialize(final GroupStore value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
      jgen.writeStartObject();
      jgen.writeObjectField("groups", new ArrayList<>(value.groups.values()));
      jgen.writeEndObject();
    }
  }

  public static class GroupStoreDeserializer extends JsonDeserializer<GroupStore> {
    @Override
    public GroupStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      GroupStore store = new GroupStore();
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      if (!node.has("groups")) {
        return store;
      }
      for (JsonNode n : node.get("groups")) {
        GroupInfo g = jsonProcessor.treeToValue(n, GroupInfo.class);
        store.groups.put(Base64.encodeBytes(g.groupId), g);
      }

      return store;
    }
  }
}
