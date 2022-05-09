package io.finn.signald.clientprotocol.v1.exceptions;

import io.finn.signald.annotations.Doc;
import org.apache.logging.log4j.LogManager;

@Doc("returned in response to use v1 groups, which are no longer supported")
public class UnsupportedGroupError extends ExceptionWrapper {
  public UnsupportedGroupError() { LogManager.getLogger().error("v1 group support has been removed: https://gitlab.com/signald/signald/-/issues/224"); }
}
