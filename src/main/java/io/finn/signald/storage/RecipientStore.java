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
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@JsonSerialize(using = RecipientStore.RecipientStoreSerializer.class)
@JsonDeserialize(using = RecipientStore.RecipientStoreDeserializer.class)
public class RecipientStore {
  private static final Logger logger = LogManager.getLogger();
  private static final ObjectMapper mapper = JSONUtil.GetMapper();
  private List<JsonAddress> addresses = new ArrayList<>();

  public RecipientStore() {}

  public void migrateToDB(UUID u) throws SQLException {
    RecipientsTable table = new RecipientsTable(u);
    logger.info("migrating " + addresses.size() + " recipients to the database");

    Iterator<JsonAddress> iterator = addresses.iterator();
    while (iterator.hasNext()) {
      JsonAddress entry = iterator.next();
      table.get(entry.getSignalServiceAddress());
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
