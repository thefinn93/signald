package io.finn.signald;

import io.finn.signald.db.AccountDataTable;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;

public class AccountRepair {
  private final static Logger logger = LogManager.getLogger();
  public final static int ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS = 1;

  public static void repairAccountIfNeeded(Account account) throws SQLException {
    int lastAccountRepair = AccountDataTable.getInt(account.getACI(), AccountDataTable.Key.LAST_ACCOUNT_REPAIR);

    if (lastAccountRepair < ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS) {
      refreshAllGroups(account);
    }
  }

  private static void refreshAllGroups(Account account) {
    logger.info("refreshing all groups for account {} (info at https://gitlab.com/signald/signald/-/issues/271)", Util.redact(account.getACI()));
    try {
      Groups groups = account.getGroups();
      List<GroupsTable.Group> allGroups = account.getGroupsTable().getAll();
      logger.debug("refreshing {} groups", allGroups.size());
      for (GroupsTable.Group group : allGroups) {
        try {
          logger.debug("refreshing group {}", group.getIdString());
          groups.getGroup(group.getSecretParams(), -1);
        } catch (InvalidInputException | InvalidGroupStateException | IOException | VerificationFailedException | SQLException e) {
          logger.error("error refreshing group {}", group.getIdString());
        }
      }
      AccountDataTable.set(account.getACI(), AccountDataTable.Key.LAST_ACCOUNT_REPAIR, ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS);
    } catch (NoSuchAccountException | ServerNotFoundException | IOException | InvalidProxyException | SQLException e) {
      logger.error("error repairing groups for account {}", account.getACI().toString());
    }
  }
}
