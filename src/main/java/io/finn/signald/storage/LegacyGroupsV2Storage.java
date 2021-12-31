/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.finn.signald.Account;
import io.finn.signald.db.GroupCredentialsTable;
import io.finn.signald.db.GroupsTable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.whispersystems.util.Base64;

public class LegacyGroupsV2Storage {
  private static final Logger logger = LogManager.getLogger();
  @JsonProperty private Map<Integer, JsonAuthCredential> credentials;
  @JsonProperty private List<Group> groups;

  public LegacyGroupsV2Storage() {
    credentials = new HashMap<>();
    groups = new ArrayList<>();
  }

  public boolean migrateToDB(Account account) throws SQLException {
    boolean needsSave = false;

    if (credentials != null) {
      GroupCredentialsTable credentialsTable = new GroupCredentialsTable(account.getACI());
      HashMap<Integer, AuthCredentialResponse> credentialResponseHashMap = new HashMap<>();
      for (Map.Entry<Integer, JsonAuthCredential> entry : credentials.entrySet()) {
        credentialResponseHashMap.put(entry.getKey(), entry.getValue().credential);
      }
      credentialsTable.setCredentials(credentialResponseHashMap);
      credentials = null;
      needsSave = true;
    }

    if (groups != null) {
      GroupsTable groupsTable = account.getGroupsTable();
      for (Group g : groups) {
        groupsTable.upsert(g.getMasterKey(), g.revision, g.getGroup(), g.getDistributionId(), g.getLastAvatarFetch());
      }
      groups = null;
      needsSave = true;
    }

    return needsSave;
  }

  @JsonSerialize(using = JsonAuthCredential.JsonAuthCredentialSerializer.class)
  @JsonDeserialize(using = JsonAuthCredential.JsonAuthCredentialDeserializer.class)
  static class JsonAuthCredential {
    AuthCredentialResponse credential;
    JsonAuthCredential(AuthCredentialResponse c) { credential = c; }

    public static class JsonAuthCredentialSerializer extends JsonSerializer<JsonAuthCredential> {
      @Override
      public void serialize(final JsonAuthCredential value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException {
        jgen.writeString(Base64.encodeBytes(value.credential.serialize()));
      }
    }

    public static class JsonAuthCredentialDeserializer extends JsonDeserializer<JsonAuthCredential> {
      @Override
      public JsonAuthCredential deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        AuthCredentialResponse c;
        try {
          c = new AuthCredentialResponse(Base64.decode(jsonParser.readValueAs(String.class)));
        } catch (InvalidInputException e) {
          logger.error("error parsing auth credential stored in legacy storage");
          throw new IOException("failed to deserialize group auth credentials");
        }
        return new JsonAuthCredential(c);
      }
    }
  }
}
