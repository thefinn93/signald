package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;

@Doc("Broadcast to subscribed clients when there is a state change from the storage service")
public class StorageChange {
  @Doc("Seems to behave like the group version numbers and increments every time the state changes") public long version;
  public StorageChange(long version) { this.version = version; }
}
