package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.ExampleValue;
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

/**
 * TODO: Add timestamp when libsignal-service supports it. The proto currently has a timestamp for the Unix millis
 *  of ban timestamp: https://github.com/signalapp/storage-service/commit/8e6da679d95ea6410ab45de03b0b19fc353c763b
 */
public class BannedGroupMember {
  @ExampleValue(ExampleValue.REMOTE_UUID) public final String uuid;

  public BannedGroupMember(DecryptedBannedMember d) { uuid = UuidUtil.fromByteStringOrUnknown(d.getUuid()).toString(); }

  public BannedGroupMember(BannedGroupMember otherForCopy) { uuid = otherForCopy.uuid; }
}
