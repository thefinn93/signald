package io.finn.signald.util;

import io.finn.signald.Account;
import io.finn.signald.SessionLock;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.push.DistributionId;

public class SenderKeyUtil {
  /**
   * Clears the state for a sender key session we created. It will naturally get re-created when it is next needed,
   * rotating the key.
   */
  public static void rotateOurKey(Account account, DistributionId distributionId)
      throws NoSuchAccountException, SQLException, ServerNotFoundException, IOException, InvalidProxyException {
    SessionLock lock = account.getSignalDependencies().getSessionLock();
    try (SignalSessionLock.Lock ignored = lock.acquire()) {
      Database.Get(account.getACI()).SenderKeysTable.deleteAllFor(account.getACI().toString(), distributionId);
      Database.Get(account.getACI()).SenderKeySharedTable.deleteAllFor(distributionId);
    }
  }
}
