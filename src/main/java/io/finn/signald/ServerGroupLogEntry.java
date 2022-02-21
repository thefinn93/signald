package io.finn.signald;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.slf4j.LoggerFactory;

/**
 * Pair of a group state and optionally the corresponding change from the server.
 * <p>
 * Either the group or change may be empty.
 * <p>
 * Changes are typically not available for pending members.
 */
class ServerGroupLogEntry {

  private final static Logger logger = LogManager.getLogger();

  @Nullable private final DecryptedGroup group;
  @Nullable private final DecryptedGroupChange change;

  ServerGroupLogEntry(@Nullable DecryptedGroup group, @Nullable DecryptedGroupChange change) {
    if (change != null && group != null && group.getRevision() != change.getRevision()) {
      logger.warn("Ignoring change with revision number not matching group");
      change = null;
    }

    if (change == null && group == null) {
      throw new AssertionError();
    }

    this.group = group;
    this.change = change;
  }

  @Nullable
  DecryptedGroup getGroup() {
    return group;
  }

  @Nullable
  DecryptedGroupChange getChange() {
    return change;
  }

  int getRevision() {
    if (group != null)
      return group.getRevision();
    else if (change != null)
      return change.getRevision();
    else
      throw new AssertionError();
  }
}
