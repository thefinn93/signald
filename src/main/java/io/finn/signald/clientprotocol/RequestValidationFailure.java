/*
 * Copyright (C) 2021 Finn Herzfeld
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
