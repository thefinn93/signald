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

package io.finn.signald.exceptions;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

// JsonifyableException is an exception that doesn't show a stack trace in the log, just report to the client, because they're not actually problems
public class JsonifyableException extends Exception {
  private boolean unexpected = false;

  public JsonifyableException() {}

  public JsonifyableException(String message) { super(message); }

  public JsonifyableException(String message, Throwable cause) { super(message, cause); }

  public JsonifyableException(Throwable cause) { super(cause); }

  public void setUnexpected(boolean u) { unexpected = u; }

  @JsonIgnore
  public boolean isUnexpected() {
    return unexpected;
  }

  @JsonIgnore
  public String getType() {
    return this.getClass().getSimpleName();
  }

  @JsonGetter("stackTrace")
  public String getJSONStackTrace() {
    return null;
  }

  @JsonGetter("suppressedExceptions")
  public String getJSONSuppressedExceptions() {
    return null;
  }

  @JsonGetter("suppressed")
  public String getJSONSuppressed() {
    return null;
  }

  @JsonGetter("ourStackTrace")
  public String getJSONOurStackTrace() {
    return null;
  }

  @JsonGetter("detailMessage")
  public String getJSONDetailMessage() {
    return null;
  }

  @JsonGetter("localizedMessage")
  public String getJSONLocalizedMessage() {
    return null;
  }

  @JsonGetter("cause")
  public String getJSONCause() {
    return null;
  }
}
