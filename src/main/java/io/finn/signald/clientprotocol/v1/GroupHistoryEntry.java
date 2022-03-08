package io.finn.signald.clientprotocol.v1;

import io.finn.signald.db.IGroupsTable;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry;

public class GroupHistoryEntry {
  public JsonGroupV2Info group;
  public GroupChange change;

  public GroupHistoryEntry(IGroupsTable.IGroup dbGroup, DecryptedGroupHistoryEntry entry) {
    group = entry.getGroup().transform((protoGroup) -> new JsonGroupV2Info(dbGroup.getMasterKey(), protoGroup).sanitized()).orNull();
    change = entry.getChange().transform(GroupChange::new).orNull();
  }
}
