package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage;

public class PagingData {
  @JsonProperty("has_more_pages") public boolean hasMorePages;
  @JsonProperty("next_page_revision") public int nextPageRevision;

  public PagingData(GroupHistoryPage.PagingData pagingData) {
    hasMorePages = pagingData.hasMorePages();
    nextPageRevision = pagingData.getNextPageRevision();
  }
}
