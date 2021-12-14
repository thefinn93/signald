package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;

@Doc("A message containing a single boolean, usually as a response")
public class BooleanMessage {
  public final boolean value;

  public BooleanMessage(boolean result) { this.value = result; }
}
