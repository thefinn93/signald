package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;

@Doc("indicates signald received an http 500 status code from the server")
public class SignalServerError extends ExceptionWrapper {
  int status;

  public SignalServerError(NonSuccessfulResponseCodeException e) { status = e.getCode(); }
}
