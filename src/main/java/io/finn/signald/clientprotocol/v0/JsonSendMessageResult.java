/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.ExampleValue;
import org.asamk.signal.util.Hex;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

@Deprecated(1641027661)
public class JsonSendMessageResult {
  public JsonAddress address;
  public JsonSendSuccess success;
  @ExampleValue("false") public boolean networkFailure;
  @ExampleValue("false") public boolean unregisteredFailure;
  public String identityFailure;

  public JsonSendMessageResult(SendMessageResult result) {
    address = new JsonAddress(result.getAddress());
    success = new JsonSendSuccess(result.getSuccess());
    networkFailure = result.isNetworkFailure();
    unregisteredFailure = result.isUnregisteredFailure();
    if (result.getIdentityFailure() != null) {
      identityFailure = Hex.toStringCondensed(result.getIdentityFailure().getIdentityKey().serialize()).trim();
    }
  }
}