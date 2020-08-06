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
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@JsonSerialize(using=RecipientStore.RecipientStoreSerializer.class)
@JsonDeserialize(using=RecipientStore.RecipientStoreDeserializer.class)
public class RecipientStore {
    private static final Logger logger = LogManager.getLogger();
    private static final ObjectMapper mapper = JSONUtil.GetMapper();
    private List<JsonAddress> addresses = new ArrayList<>();

    public RecipientStore() {}

    private void add(SignalServiceAddress a) {
        if(a.getNumber().isPresent() && a.getUuid().isPresent()) {
            JsonAddress jsonAddress = new JsonAddress(a);
            logger.debug("creating new recipientStore entry: " + jsonAddress.toRedactedString());
            addresses.add(jsonAddress);
        } else {
            logger.debug("not storing unresolved, partial address: " + new JsonAddress(a).toRedactedString());
        }
    }

    public SignalServiceAddress resolve(SignalServiceAddress partial) {
        for(JsonAddress i : addresses) {
            if(i.getSignalServiceAddress().matches(partial)) {
                logger.debug("Updating " + i.toRedactedString());
                i.update(partial);
                logger.debug("Updated to " + i.toRedactedString());
                return i.getSignalServiceAddress();
            }
        }

        add(partial);
        return partial;
    }

    public static class RecipientStoreDeserializer extends JsonDeserializer<RecipientStore> {

        @Override
        public RecipientStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            RecipientStore store = new RecipientStore();
            if(node.isArray()) {
                for(JsonNode recipient : node) {
                    JsonAddress jsonAddress = mapper.treeToValue(recipient, JsonAddress.class);
                    store.addresses.add(jsonAddress);
                }
            }
            return store;
        }
    }

    public static class RecipientStoreSerializer extends JsonSerializer<RecipientStore> {

        @Override
        public void serialize(RecipientStore store, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for(JsonAddress address : store.addresses) {
                json.writeObject(address);
            }
            json.writeEndArray();
        }
    }
}
