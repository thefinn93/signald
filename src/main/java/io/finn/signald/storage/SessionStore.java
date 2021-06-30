/*
 * Copyright (C) 2021 Finn Herzfeld
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
import io.finn.signald.db.SessionsTable;
import io.finn.signald.util.AddressUtil;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

@JsonSerialize(using = SessionStore.SessionStoreSerializer.class)
@JsonDeserialize(using = SessionStore.SessionStoreDeserializer.class)
public class SessionStore {
  private static final Logger logger = LogManager.getLogger();

  private AddressResolver resolver;
  private static ObjectMapper mapper = JSONUtil.GetMapper();

  public List<SessionInfo> sessions = new ArrayList<>();

  public SessionStore() {}

  public SessionStore(AddressResolver r) { resolver = r; }

  public void setResolver(final AddressResolver resolver) { this.resolver = resolver; }

  private SignalServiceAddress resolveSignalServiceAddress(String identifier) {
    if (resolver != null) {
      return resolver.resolve(identifier);
    } else {
      return AddressUtil.fromIdentifier(identifier);
    }
  }

  public synchronized SessionRecord loadSession(SignalProtocolAddress address) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
    for (SessionInfo info : sessions) {
      if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
        try {
          return new SessionRecord(info.record);
        } catch (IOException e) {
          final SessionRecord sessionRecord = new SessionRecord();
          info.record = sessionRecord.serialize();
          return sessionRecord;
        }
      }
    }

    return new SessionRecord();
  }

  public synchronized List<SessionInfo> getSessions() { return sessions; }

  public synchronized List<Integer> getSubDeviceSessions(String name) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(name);

    List<Integer> deviceIds = new LinkedList<>();
    for (SessionInfo info : sessions) {
      if (info.address.matches(serviceAddress) && info.deviceId != 1) {
        deviceIds.add(info.deviceId);
      }
    }

    return deviceIds;
  }

  public synchronized void storeSession(SignalProtocolAddress address, SessionRecord record) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
    for (SessionInfo info : sessions) {
      if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
        if (!info.address.getUuid().isPresent() || !info.address.getNumber().isPresent()) {
          info.address = serviceAddress;
        }
        info.record = record.serialize();
        return;
      }
    }

    sessions.add(new SessionInfo(serviceAddress, address.getDeviceId(), record.serialize()));
  }

  public synchronized boolean containsSession(SignalProtocolAddress address) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
    for (SessionInfo info : sessions) {
      if (info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId()) {
        return true;
      }
    }
    return false;
  }

  public synchronized void deleteSession(SignalProtocolAddress address) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(address.getName());
    sessions.removeIf(info -> info.address.matches(serviceAddress) && info.deviceId == address.getDeviceId());
  }

  public synchronized void deleteAllSessions(String name) {
    SignalServiceAddress serviceAddress = resolveSignalServiceAddress(name);
    deleteAllSessions(serviceAddress);
  }

  public synchronized void deleteAllSessions(SignalServiceAddress serviceAddress) { sessions.removeIf(info -> info.address.matches(serviceAddress)); }

  public void migrateToDB(UUID u) {
    SessionsTable table = new SessionsTable(u);
    logger.info("migrating " + sessions.size() + " sessions to the database");
    Iterator<SessionInfo> iterator = sessions.iterator();
    while (iterator.hasNext()) {
      SessionInfo entry = iterator.next();
      try {
        if (entry.record == null) {
          continue;
        }
        table.storeSession(new SignalProtocolAddress(entry.address.getIdentifier(), entry.deviceId), new SessionRecord(entry.record));
        iterator.remove();
      } catch (IOException e) {
        logger.warn("failed to migrate session record", e);
      }
    }
  }

  public static class SessionStoreDeserializer extends JsonDeserializer<SessionStore> {

    @Override
    public SessionStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      JsonNode tree = jsonParser.getCodec().readTree(jsonParser);
      SessionStore sessionStore = new SessionStore();
      if (tree.isArray()) {
        for (JsonNode node : tree) {
          SessionInfo sessionInfo = mapper.treeToValue(node, SessionInfo.class);
          sessionStore.sessions.add(sessionInfo);
        }
      }

      return sessionStore;
    }
  }

  public static class SessionStoreSerializer extends JsonSerializer<SessionStore> {

    @Override
    public void serialize(SessionStore jsonSessionStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
      json.writeStartArray();
      for (SessionInfo sessionInfo : jsonSessionStore.sessions) {
        json.writeObject(sessionInfo);
      }
      json.writeEndArray();
    }
  }

  public static class SessionInfo {
    public SignalServiceAddress address;
    public int deviceId;
    public byte[] record;

    public SessionInfo() {}

    public SessionInfo(final SignalServiceAddress address, final int deviceId, final byte[] sessionRecord) {
      this.address = address;
      this.deviceId = deviceId;
      this.record = sessionRecord;
    }

    public JsonAddress getAddress() { return new JsonAddress(address); }

    public void setAddress(JsonAddress a) { address = a.getSignalServiceAddress(); }

    public void setName(String name) { address = new SignalServiceAddress(null, name); }
  }
}
