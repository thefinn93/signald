package io.finn.signald.clientprotocol.v1.exceptions;

public class AttachmentTooLargeError extends ExceptionWrapper {
  public String filename;
  public AttachmentTooLargeError(String filename) { this.filename = filename; }
}
