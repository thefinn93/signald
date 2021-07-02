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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.ExceptionWrapper;
import io.finn.signald.util.RequestUtil;

@Doc("Wraps all incoming messages after a v1 subscribe request is issued")
public class ClientMessageWrapper {
  @Doc("the type of object to expect in the `data` field") public String type;
  @Doc("the version of the object in the `data` field") public String version;
  @Doc("the incoming object. The structure will vary from message to message, see `type` and `version` fields") public Object data;
  @Doc("true if the incoming message represents an error") public Boolean error;

  public ClientMessageWrapper(Object o) {
    Class<?> c = o.getClass();
    type = RequestUtil.getType(c);
    version = RequestUtil.getVersion(c);
    data = o;
  }

  public static ClientMessageWrapper Exception(Throwable exception) {
    ClientMessageWrapper wrapper = new ClientMessageWrapper(new ExceptionWrapper(exception));
    wrapper.error = true;
    return wrapper;
  }
}
