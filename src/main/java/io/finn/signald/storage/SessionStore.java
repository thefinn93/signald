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
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.*;


@JsonDeserialize(using=SessionStore.SessionStoreDeserializer.class)
@JsonSerialize(using=SessionStore.SessionStoreSerializer.class)
public class SessionStore implements org.whispersystems.libsignal.state.SessionStore {
    private final Map<SignalProtocolAddress, byte[]> sessions = new HashMap<>();

    @Override
    public synchronized SessionRecord loadSession(SignalProtocolAddress remoteAddress) {
        try {
            if (containsSession(remoteAddress)) {
                return new SessionRecord(sessions.get(remoteAddress));
            } else {
                return new SessionRecord();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public synchronized List<Integer> getSubDeviceSessions(String name) {
        List<Integer> deviceIds = new LinkedList<>();
        for (SignalProtocolAddress key : sessions.keySet()) {
            if (key.getName().equals(name) &&
                    key.getDeviceId() != 1) {
                deviceIds.add(key.getDeviceId());
            }
        }
        return deviceIds;
    }

    @Override
    public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
        sessions.put(address, record.serialize());
    }

    @Override
    public synchronized boolean containsSession(SignalProtocolAddress address) {
        return sessions.containsKey(address);
    }

    @Override
    public synchronized void deleteSession(SignalProtocolAddress address) {
        sessions.remove(address);
    }

    @Override
    public synchronized void deleteAllSessions(String name) {
        for (SignalProtocolAddress key : new ArrayList<>(sessions.keySet())) {
            if (key.getName().equals(name)) {
                sessions.remove(key);
            }
        }
    }

    public static class SessionStoreDeserializer extends JsonDeserializer<SessionStore> {
        @Override
        public SessionStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode node = jsonParser.getCodec().readTree(jsonParser);
            Map<SignalProtocolAddress, byte[]> sessionMap = new HashMap<>();
            if (node.isArray()) {
                for (JsonNode session : node) {
                    String sessionName = session.get("name").asText();
                    try {
                        sessionMap.put(new SignalProtocolAddress(sessionName, session.get("deviceId").asInt()), org.whispersystems.util.Base64.decode(session.get("record").asText()));
                    } catch (IOException e) {
                        System.out.println(String.format("Error while decoding session for: %s", sessionName));
                    }
                }
            }
            SessionStore sessionStore = new SessionStore();
            sessionStore.sessions.putAll(sessionMap);
            return sessionStore;
        }
    }

    public static class SessionStoreSerializer extends JsonSerializer<SessionStore> {
        @Override
        public void serialize(SessionStore jsonSessionStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
            json.writeStartArray();
            for (Map.Entry<SignalProtocolAddress, byte[]> preKey : jsonSessionStore.sessions.entrySet()) {
                json.writeStartObject();
                json.writeStringField("name", preKey.getKey().getName());
                json.writeNumberField("deviceId", preKey.getKey().getDeviceId());
                json.writeStringField("record", Base64.encodeBytes(preKey.getValue()));
                json.writeEndObject();
            }
            json.writeEndArray();
        }
    }
}
