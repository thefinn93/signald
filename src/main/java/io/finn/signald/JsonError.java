package io.finn.signald;

class JsonError {
  public int error;
  public String message;

  JsonError(int error, String message) {
    this.error = error;
    this.message = message;
  }
}
