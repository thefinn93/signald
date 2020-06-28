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
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonSerialize(using=ContactStore.ContactStoreSerializer.class)
@JsonDeserialize(using=ContactStore.ContactStoreDeserializer.class)
public class ContactStore {
    private Map<String, ContactInfo> contacts = new HashMap<>();

    private static final ObjectMapper jsonProcessor = new ObjectMapper();

    public void updateContact(ContactInfo contact) {
        contacts.put(contact.address.number, contact);
    }

    public ContactInfo getContact(String number) {
        return contacts.get(number);
    }

    public ContactInfo getContact(SignalServiceAddress address) {
        return contacts.get(address.getLegacyIdentifier());
    }

    public List<ContactInfo> getContacts() {
        return new ArrayList<>(contacts.values());
    }

    public void clear() {
        contacts.clear();
    }

    public static class ContactStoreSerializer extends JsonSerializer<ContactStore> {
        @Override
        public void serialize(final ContactStore value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
            jgen.writeStartObject();
            jgen.writeObjectField("contacts", value.contacts.values());
            jgen.writeEndObject();
        }
    }

    public static class ContactStoreDeserializer extends JsonDeserializer<ContactStore> {
        @Override
        public ContactStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            ContactStore store = new ContactStore();
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            if(!node.has("contacts")) {
                return store;
            }
            for (JsonNode n : node.get("contacts")) {
                ContactInfo c = jsonProcessor.treeToValue(n, ContactInfo.class);
                store.contacts.put(c.address.number, c);
            }

            return store;
        }
    }
}
