/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;

@ProtocolType("version")
public class VersionRequest implements RequestType<JsonVersionMessage> {
  @Override
  public JsonVersionMessage run(Request request) {
    return new JsonVersionMessage();
  }
}
