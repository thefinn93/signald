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
