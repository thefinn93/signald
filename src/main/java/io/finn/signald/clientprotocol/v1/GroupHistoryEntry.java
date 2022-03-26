package io.finn.signald.clientprotocol.v1;

import io.finn.signald.db.IGroupsTable;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupHistoryEntry;

public class GroupHistoryEntry {
  public JsonGroupV2Info group;
  public GroupChange change;

  public GroupHistoryEntry(IGroupsTable.IGroup dbGroup, DecryptedGroupHistoryEntry entry) {
    group = entry.getGroup().isPresent() ? new JsonGroupV2Info(dbGroup.getMasterKey(), entry.getGroup().get()).sanitized() : null;
    change = entry.getChange().isPresent() ? new GroupChange(entry.getChange().get()) : null;
  }
}
