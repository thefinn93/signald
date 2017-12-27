package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
class MessageWrapper {
  int id;
  String type;
  Object data;

  public MessageWrapper(String type, Object data, int id) {
    this.type = type;
    this.data = data;
    this.id = id;
  }

  public MessageWrapper(String type, Object data) {
    this(type, data, 0);
  }
}
