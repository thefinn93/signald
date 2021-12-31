/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.util.RequestUtil;

@Doc("Wraps all incoming messages sent to the client after a v1 subscribe request is issued")
public class ClientMessageWrapper {
  @Doc("the type of object to expect in the `data` field") public String type;
  @Doc("the version of the object in the `data` field") public String version;
  @Doc("the incoming object. The structure will vary from message to message, see `type` and `version` fields") public Object data;
  @Doc("true if the incoming message represents an error") public Boolean error;
  @Doc("the account this message is from") public String account;

  public ClientMessageWrapper(String account, Object o) {
    this.account = account;
    Class<?> c = o.getClass();
    type = RequestUtil.getType(c);
    version = RequestUtil.getVersion(c);
    data = o;
  }
}
