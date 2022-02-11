package io.finn.signald.clientprotocol.v1.exceptions;

public class ScanTimeoutError extends ExceptionWrapper {
  public ScanTimeoutError(Exception e) { super(e); }
}
