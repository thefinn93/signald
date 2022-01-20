package io.finn.signald.clientprotocol.v1.exceptions;

import java.util.concurrent.TimeoutException;

public class ScanTimeoutError extends ExceptionWrapper {
  public ScanTimeoutError(TimeoutException e) { super(e); }
}
