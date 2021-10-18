/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Doc;
import java.util.List;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;

public class ProofRequiredError extends ExceptionWrapper {
  public final String token;
  @Doc("possible list values are RECAPTCHA and PUSH_CHALLENGE") public final List<String> options;
  @Doc("value in seconds") @JsonProperty("retry_after") public final long retryAfter;

  public ProofRequiredError(ProofRequiredException e) {
    super(e);
    token = e.getToken();
    options = e.getOptions().stream().map(Enum::name).collect(Collectors.toList());
    retryAfter = e.getRetryAfterSeconds();
  }
}
