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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@JsonSerialize(using = RecipientStore.RecipientStoreSerializer.class)
@JsonDeserialize(using = RecipientStore.RecipientStoreDeserializer.class)
public class RecipientStore {
  private static final Logger logger = LogManager.getLogger();
  private static final ObjectMapper mapper = JSONUtil.GetMapper();
  private List<JsonAddress> addresses = new ArrayList<>();

  public RecipientStore() {}

  public void migrateToDB(Account account) throws SQLException {
    logger.info("migrating " + addresses.size() + " recipients to the database");

    Iterator<JsonAddress> iterator = addresses.iterator();
    while (iterator.hasNext()) {
      JsonAddress entry = iterator.next();
      try {
        Database.Get(account.getACI()).RecipientsTable.get(entry.number, entry.getACI());
      } catch (IOException e) {
        logger.warn("error migrating recipient to db: ", e);
      }
      iterator.remove();
    }
  }

  public static class RecipientStoreDeserializer extends JsonDeserializer<RecipientStore> {

    @Override
    public RecipientStore deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      JsonNode node = jsonParser.getCodec().readTree(jsonParser);
      RecipientStore store = new RecipientStore();
      if (node.isArray()) {
        for (JsonNode recipient : node) {
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
      for (JsonAddress address : store.addresses) {
        json.writeObject(address);
      }
      json.writeEndArray();
    }
  }
}
