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
import io.finn.signald.Account;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

@JsonSerialize(using = SessionStore.SessionStoreSerializer.class)
@JsonDeserialize(using = SessionStore.SessionStoreDeserializer.class)
public class SessionStore {
  private static final Logger logger = LogManager.getLogger();

  private static ObjectMapper mapper = JSONUtil.GetMapper();

  public List<SessionInfo> sessions = new ArrayList<>();

  public SessionStore() {}

  public synchronized List<SessionInfo> getSessions() { return sessions; }

  public void migrateToDB(Account account) {
    logger.info("migrating " + sessions.size() + " sessions to the database");
    Iterator<SessionInfo> iterator = sessions.iterator();
    while (iterator.hasNext()) {
      SessionInfo entry = iterator.next();
      try {
        if (entry.record == null) {
          continue;
        }
        Database.Get(account.getACI()).SessionsTable.storeSession(new SignalProtocolAddress(entry.address.getIdentifier(), entry.deviceId), new SessionRecord(entry.record));
        iterator.remove();
      } catch (InvalidMessageException e) {
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
