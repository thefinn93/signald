package io.finn.signald.clientprotocol.v1.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

public class UnregisteredUserError extends ExceptionWrapper {
  @JsonProperty("e164_number") final String e164number;
  public UnregisteredUserError(UnregisteredUserException e) {
    super(e);
    e164number = e.getE164Number();
  }
}
