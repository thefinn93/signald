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

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ProtocolIgnore;

@Doc("a warpper for exceptions that are not in the documented protocol")
public class ExceptionWrapper extends Exception {
  private boolean unexpected = false;

  public ExceptionWrapper() {}

  public ExceptionWrapper(Throwable cause) { super(cause); }

  public static ExceptionWrapper fromThrowable(Throwable e) {
    if (e instanceof ExceptionWrapper) {
      return (ExceptionWrapper)e;
    }
    return new ExceptionWrapper(e);
  }

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
