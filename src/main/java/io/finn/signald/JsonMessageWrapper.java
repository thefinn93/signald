/**
 * Copyright (C) 2018 Finn Herzfeld
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

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
class JsonMessageWrapper {
  String id;
  String type;
  Object data;
  String exception;

  public JsonMessageWrapper(String type, Object data, String id) {
    this.type = type;
    this.data = data;
    this.id = id;
  }

  public JsonMessageWrapper(String type, Object data, Throwable e) {
    this.type = type;
    this.data = data;
    if(e != null) {
      this.exception = e.toString();
    }
  }
}
