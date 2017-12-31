package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
class JsonMessageWrapper {
  String id;
  String type;
  Object data;

  public JsonMessageWrapper(String type, Object data, String id) {
    this.type = type;
    this.data = data;
    this.id = id;
  }

  public JsonMessageWrapper(String type, Object data) {
    this(type, data, null);
  }
}
