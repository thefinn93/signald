/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol;

import com.fasterxml.jackson.annotation.JsonGetter;
import io.finn.signald.annotations.ProtocolIgnore;
import java.util.ArrayList;
import java.util.List;

public class RequestValidationFailure extends Exception {
  public List<String> validationResults;

  public RequestValidationFailure(List<String> p) {
    super("input validation failed, please check the request and try again.");
    validationResults = p;
  }

  public RequestValidationFailure(String p) {
    super("input validation failed, please check the request and try again.");
    validationResults = new ArrayList<>();
    validationResults.add(p);
  }

  @JsonGetter("stackTrace")
  @ProtocolIgnore
  public String getJSONStackTrace() {
    return null;
  }

  @JsonGetter("suppressedExceptions")
  @ProtocolIgnore
  public String getJSONSuppressedExceptions() {
    return null;
  }

  @JsonGetter("suppressed")
  @ProtocolIgnore
  public String getJSONSuppressed() {
    return null;
  }

  @JsonGetter("ourStackTrace")
  @ProtocolIgnore
  public String getJSONOurStackTrace() {
    return null;
  }

  @JsonGetter("detailMessage")
  @ProtocolIgnore
  public String getJSONDetailMessage() {
    return null;
  }

  @JsonGetter("localizedMessage")
  @ProtocolIgnore
  public String getJSONLocalizedMessage() {
    return null;
  }

  @JsonGetter("cause")
  @ProtocolIgnore
  public String getJSONCause() {
    return null;
  }
}
