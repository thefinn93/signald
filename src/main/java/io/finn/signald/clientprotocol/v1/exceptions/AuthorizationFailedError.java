package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

@Doc(
    "Indicates the server rejected our credentials or a failed group update. Typically means the linked device was removed by the primary device, or that the account was re-registered. For group updates, this can indicate that we lack permissions.")
public class AuthorizationFailedError extends ExceptionWrapper {
  public static final String DEFAULT_ERROR_DOC =
      "Can be caused if signald is setup as a linked device that has been removed by the primary device. If trying to update a group, this can also be caused if group permissions don't allow the update  (e.g. current role insufficient or not a member).";

  public AuthorizationFailedError(AuthorizationFailedException e) { super(e); }
}
