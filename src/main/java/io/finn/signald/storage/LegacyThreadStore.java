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

@JsonSerialize(using= LegacyThreadStore.ThreadStoreSerializer.class)
@JsonDeserialize(using= LegacyThreadStore.ThreadStoreDeserializer.class)
public class LegacyThreadStore {

    private Map<String, LegacyThreadInfo> threads = new HashMap<>();

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    public void updateThread(LegacyThreadInfo thread) {
        threads.put(thread.id, thread);
    }

    public LegacyThreadInfo getThread(String id) {
        return threads.get(id);
    }

    public List<LegacyThreadInfo> getThreads() {
        return new ArrayList<>(threads.values());
    }

    public static class ThreadStoreSerializer extends JsonSerializer<LegacyThreadStore> {
        @Override
        public void serialize(final LegacyThreadStore value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeObjectField("threads", value.threads.values());
            jgen.writeEndObject();
        }
    }

    public static class ThreadStoreDeserializer extends JsonDeserializer<LegacyThreadStore> {
        @Override
        public LegacyThreadStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            LegacyThreadStore store = new LegacyThreadStore();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            if(!node.has("threads")) {
                return store;
            }
            for (JsonNode n : node.get("threads")) {
                LegacyThreadInfo t = jsonProcessor.treeToValue(n, LegacyThreadInfo.class);
                store.threads.put(t.id, t);
            }

            return store;
        }
    }
}
