/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.ProofRequiredError;
import org.asamk.signal.util.Hex;
import org.whispersystems.signalservice.api.messages.SendMessageResult;

public class JsonSendMessageResult {
  public JsonAddress address;
  public SendSuccess success;
  @ExampleValue("false") public boolean networkFailure;
  @ExampleValue("false") public boolean unregisteredFailure;
  public String identityFailure;
  @JsonProperty("proof_required_failure") public ProofRequiredError proofRequiredFailure;

  public JsonSendMessageResult(SendMessageResult result) {
    address = new JsonAddress(result.getAddress());
    if (result.getSuccess() != null) {
      success = new SendSuccess(result.getSuccess());
    }
    networkFailure = result.isNetworkFailure();
    unregisteredFailure = result.isUnregisteredFailure();
    if (result.getIdentityFailure() != null) {
      identityFailure = Hex.toStringCondensed(result.getIdentityFailure().getIdentityKey().serialize()).trim();
    }
    if (result.getProofRequiredFailure() != null) {
      proofRequiredFailure = new ProofRequiredError(result.getProofRequiredFailure());
    }
  }
}
