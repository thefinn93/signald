package io.finn.signald;

class JsonStatusMessage {
  public int msg_number;
  public String message;
  public boolean error;

  JsonStatusMessage(int msgNumber, String message, boolean error) {
    this.msg_number = msgNumber;
    this.message = message;
    this.error = error;
  }
}
