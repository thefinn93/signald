/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.clientprotocol.v1.exceptions.ExceptionWrapper;

@JsonInclude(Include.NON_NULL)
@Deprecated(1641027661)
public class JsonMessageWrapper {
  public String id;
  public String type;
  public Object data;
  public Object error;
  public String exception;
  @JsonProperty("error_type") public String errorType;

  public JsonMessageWrapper(String type, Object data) {
    this.type = type;
    this.data = data;
  }

  public JsonMessageWrapper(String type, Object data, String id) {
    this.type = type;
    this.data = data;
    this.id = id;
  }

  public JsonMessageWrapper(String type, Object data, Throwable e) {
    this.type = type;
    this.data = data;
    if (e != null) {
      this.exception = e.toString();
    }
  }

  public static JsonMessageWrapper error(String type, Object error, String id) {
    JsonMessageWrapper j = new JsonMessageWrapper(type, null, id);
    j.error = error;
    if (error instanceof ExceptionWrapper) {
      j.errorType = ((ExceptionWrapper)error).getType();
    } else {
      j.errorType = error.getClass().getSimpleName();
    }
    return j;
  }
}
