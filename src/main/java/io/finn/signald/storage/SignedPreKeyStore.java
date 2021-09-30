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
import io.finn.signald.Account;
import io.finn.signald.db.SignedPreKeysTable;
import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.util.Base64;

@JsonDeserialize(using = SignedPreKeyStore.SignedPreKeyStoreDeserializer.class)
@JsonSerialize(using = SignedPreKeyStore.SignedPreKeyStoreSerializer.class)
public class SignedPreKeyStore implements org.whispersystems.libsignal.state.SignedPreKeyStore {
  private static final Logger logger = LogManager.getLogger();
  private final Map<Integer, byte[]> store = new HashMap<>();

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    try {
      if (!store.containsKey(signedPreKeyId)) {
        throw new InvalidKeyIdException("No such signedprekeyrecord! " + signedPreKeyId);
      }
      return new SignedPreKeyRecord(store.get(signedPreKeyId));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    try {
      List<SignedPreKeyRecord> results = new LinkedList<>();

      for (byte[] serialized : store.values()) {
        results.add(new SignedPreKeyRecord(serialized));
      }

      return results;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    store.put(signedPreKeyId, record.serialize());
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return store.containsKey(signedPreKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    store.remove(signedPreKeyId);
  }

  public void migrateToDB(Account account) {
    SignedPreKeysTable signedPreKeysTable = new SignedPreKeysTable(account.getUUID());
    logger.info("migrating " + store.size() + " signed pre-keys to the database");
    Iterator<Map.Entry<Integer, byte[]>> iterator = store.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Integer, byte[]> entry = iterator.next();
      try {
        if (entry.getValue() == null) {
          continue;
        }
        signedPreKeysTable.storeSignedPreKey(entry.getKey(), new SignedPreKeyRecord(entry.getValue()));
        iterator.remove();
      } catch (IOException e) {
        logger.warn("failed to migrate session record", e);
      }
    }
  }

  public static class SignedPreKeyStoreDeserializer extends JsonDeserializer<SignedPreKeyStore> {
    @Override
    public SignedPreKeyStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      Map<Integer, byte[]> preKeyMap = new HashMap<>();
      if (node.isArray()) {
        for (JsonNode preKey : node) {
          Integer preKeyId = preKey.get("id").asInt();
          try {
            preKeyMap.put(preKeyId, Base64.decode(preKey.get("record").asText()));
          } catch (IOException e) {
            System.out.println(String.format("Error while decoding prekey for: %s", preKeyId));
          }
        }
      }
      SignedPreKeyStore keyStore = new SignedPreKeyStore();
      keyStore.store.putAll(preKeyMap);
      return keyStore;
    }
  }

  public static class SignedPreKeyStoreSerializer extends JsonSerializer<SignedPreKeyStore> {
    @Override
    public void serialize(SignedPreKeyStore jsonPreKeyStore, JsonGenerator json, SerializerProvider serializerProvider) throws IOException {
      json.writeStartArray();
      for (Map.Entry<Integer, byte[]> signedPreKey : jsonPreKeyStore.store.entrySet()) {
        json.writeStartObject();
        json.writeNumberField("id", signedPreKey.getKey());
        json.writeStringField("record", Base64.encodeBytes(signedPreKey.getValue()));
        json.writeEndObject();
      }
      json.writeEndArray();
    }
  }
}
