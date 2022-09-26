package io.finn.signald.clientprotocol.v1.exceptions;

public class NetworkError extends ExceptionWrapper {
  String exception;
  String message;

  public NetworkError(Exception e) {
    exception = e.getClass().getCanonicalName();
    message = e.getMessage();
  }
}
