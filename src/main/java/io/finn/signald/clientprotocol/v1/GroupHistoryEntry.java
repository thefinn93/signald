package io.finn.signald.clientprotocol.v1;

import io.finn.signald.db.GroupsTable;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry;

public class GroupHistoryEntry {
  public JsonGroupV2Info group;
  public GroupChange change;

  public GroupHistoryEntry(GroupsTable.Group dbGroup, DecryptedGroupHistoryEntry entry) {
    group = entry.getGroup().transform((protoGroup) -> new JsonGroupV2Info(dbGroup.getSignalServiceGroupV2(), protoGroup).sanitized()).orNull();
    change = entry.getChange().transform(GroupChange::new).orNull();
  }
}
