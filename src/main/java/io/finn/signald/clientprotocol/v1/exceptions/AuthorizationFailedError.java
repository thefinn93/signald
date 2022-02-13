package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

@Doc("indicates the server rejected our credentials. Typically means the linked device was removed by the primary device, or that the account was re-registered")
public class AuthorizationFailedError extends ExceptionWrapper {
  public AuthorizationFailedError(AuthorizationFailedException e) { super(e); }
}
