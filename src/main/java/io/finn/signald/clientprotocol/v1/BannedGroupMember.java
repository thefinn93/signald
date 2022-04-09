package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class BannedGroupMember {
  @ExampleValue(ExampleValue.REMOTE_UUID) public final String uuid;
  @Doc("Timestamp as milliseconds since Unix epoch of when the user was banned. This field is set by the server.") public final long timestamp;

  public BannedGroupMember(DecryptedBannedMember d) {
    uuid = UuidUtil.fromByteStringOrUnknown(d.getUuid()).toString();
    timestamp = d.getTimestamp();
  }

  public BannedGroupMember(BannedGroupMember otherForCopy) {
    uuid = otherForCopy.uuid;
    timestamp = otherForCopy.timestamp;
  }
}
