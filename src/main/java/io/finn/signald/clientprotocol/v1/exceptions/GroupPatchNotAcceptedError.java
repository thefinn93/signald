package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;

@Doc("Indicates the server rejected our group update. This can be due to errors such as trying to add a user that's already in the group.")
public class GroupPatchNotAcceptedError extends ExceptionWrapper {
  public static final String DEFAULT_ERROR_DOC = "Caused when server rejects the group update.";

  public GroupPatchNotAcceptedError(GroupPatchNotAcceptedException e) { super(e); }
}
