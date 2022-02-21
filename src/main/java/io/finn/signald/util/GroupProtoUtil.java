package io.finn.signald.util;

import com.google.protobuf.ByteString;
import io.reactivex.rxjava3.annotations.NonNull;
import java.util.List;
import java.util.UUID;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;

public class GroupProtoUtil {
  public static int findRevisionWeWereAdded(@NonNull DecryptedGroup group, @NonNull UUID uuid) throws NotInGroupException {
    ByteString bytes = UuidUtil.toByteString(uuid);
    for (DecryptedMember decryptedMember : group.getMembersList()) {
      if (decryptedMember.getUuid().equals(bytes)) {
        return decryptedMember.getJoinedAtRevision();
      }
    }
    for (DecryptedPendingMember decryptedMember : group.getPendingMembersList()) {
      if (decryptedMember.getUuid().equals(bytes)) {
        // Assume latest, we don't have any information about when pending members were invited
        return group.getRevision();
      }
    }
    throw new NotInGroupException();
  }

  public static boolean isMember(@NonNull UUID uuid, @NonNull List<DecryptedMember> membersList) {
    ByteString uuidBytes = UuidUtil.toByteString(uuid);

    for (DecryptedMember member : membersList) {
      if (uuidBytes.equals(member.getUuid())) {
        return true;
      }
    }

    return false;
  }
}
