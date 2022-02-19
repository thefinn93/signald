package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class GroupRequestingMember {
  @ExampleValue(ExampleValue.REMOTE_UUID) public String uuid;
  public long timestamp;

  public GroupRequestingMember(DecryptedRequestingMember m) {
    uuid = UuidUtil.fromByteStringOrUnknown(m.getUuid()).toString();
    timestamp = m.getTimestamp();
  }
}
