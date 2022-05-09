package io.finn.signald.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.finn.signald.util.JSONUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public interface IProfileBadgesTable {
  String ACCOUNT_UUID = "account_uuid";
  String ID = "id";
  String CATEGORY = "category";
  String NAME = "name";
  String DESCRIPTION = "description";
  String SPRITE6 = "sprite6";

  Badge get(String id) throws SQLException, JsonProcessingException;
  void set(Badge badge) throws SQLException, JsonProcessingException;

  class Badge {
    @JsonProperty private final String id;
    @JsonProperty private final String category;
    @JsonProperty private final String name;
    @JsonProperty private final String description;
    @JsonProperty private final List<String> sprites6;

    public Badge(SignalServiceProfile.Badge badge) {
      id = badge.getId();
      category = badge.getCategory();
      name = badge.getName();
      description = badge.getDescription();
      sprites6 = badge.getSprites6();
    }

    public Badge(ResultSet row) throws SQLException, JsonProcessingException {
      id = row.getString(ID);
      category = row.getString(CATEGORY);
      name = row.getString(NAME);
      description = row.getString(DESCRIPTION);
      sprites6 = JSONUtil.GetMapper().readValue(row.getString(SPRITE6), new TypeReference<>() {});
    }

    public String getId() { return id; }

    public String getCategory() { return category; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public List<String> getSprites6() { return sprites6; }
  }
}
