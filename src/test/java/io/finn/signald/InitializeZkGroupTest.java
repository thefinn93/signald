package io.finn.signald;

import io.finn.signald.db.ServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class InitializeZkGroupTest {
  @Test
  @DisplayName("initialize the zkgroup library")
  void initializeZkGroup() throws IOException, InvalidProxyException {
    GroupsUtil.GetGroupsV2Operations(ServersTable.getDefaultServer().getSignalServiceConfiguration());
  }
}
