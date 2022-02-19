package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.Doc;
import io.finn.signald.db.GroupsTable;
import java.util.List;
import java.util.stream.Collectors;

@Doc("The result of fetching a group's history along with paging data.")
public class GroupHistoryPage {
  public List<GroupHistoryEntry> results;
  @JsonProperty("paging_data") public PagingData pagingData;

  public GroupHistoryPage(GroupsTable.Group dbGroup, org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage page) {
    results = page.getResults().stream().map((result) -> new GroupHistoryEntry(dbGroup, result)).collect(Collectors.toList());
    pagingData = new PagingData(page.getPagingData());
  }
}
