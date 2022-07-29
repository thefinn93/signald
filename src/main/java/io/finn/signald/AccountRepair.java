package io.finn.signald;

import io.finn.signald.db.Database;
import io.finn.signald.db.IAccountDataTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.SendSyncRequestJob;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class AccountRepair {
  private final static Logger logger = LogManager.getLogger();
  public final static int ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS = 1;
  public final static int ACCOUNT_REPAIR_VERSION_CLEAR_SENDER_KEY_SHARED = 2;
  public final static int ACCOUNT_REPAIR_VERSION_CLEAR_CONTACTS_TABLE = 3;

  public static int getLatestVersion() { return ACCOUNT_REPAIR_VERSION_CLEAR_CONTACTS_TABLE; }

  public static void repairAccountIfNeeded(Account account) throws SQLException {
    int lastAccountRepair = Database.Get().AccountDataTable.getInt(account.getACI(), IAccountDataTable.Key.LAST_ACCOUNT_REPAIR);

    if (lastAccountRepair < ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS) {
      refreshAllGroups(account);
    }

    if (lastAccountRepair < ACCOUNT_REPAIR_VERSION_CLEAR_SENDER_KEY_SHARED) {
      clearSenderKeyShared(account);
    }

    if (lastAccountRepair < ACCOUNT_REPAIR_VERSION_CLEAR_CONTACTS_TABLE) {
      clearContactsTableIfNonPrimaryDevice(account);
    }
  }

  private static void clearSenderKeyShared(Account account) throws SQLException {
    logger.info("clearing all sender key shared state to make sure they get re-shared");
    Database.Get(account.getACI()).SenderKeySharedTable.deleteAccount(account.getACI());
    Database.Get().AccountDataTable.set(account.getACI(), IAccountDataTable.Key.LAST_ACCOUNT_REPAIR, ACCOUNT_REPAIR_VERSION_CLEAR_SENDER_KEY_SHARED);
  }

  private static void refreshAllGroups(Account account) {
    logger.info("refreshing all groups for account {} (info at https://gitlab.com/signald/signald/-/issues/271)", Util.redact(account.getACI()));
    try {
      Groups groups = account.getGroups();
      var allGroups = Database.Get(account.getACI()).GroupsTable.getAll();
      logger.debug("refreshing {} groups", allGroups.size());
      for (var group : allGroups) {
        try {
          logger.debug("refreshing group {}", group.getIdString());
          groups.getGroup(group.getSecretParams(), -1);
        } catch (AuthorizationFailedException e) {
          logger.error("authorization failed while refreshing groups. Should probably delete this account");
        } catch (InvalidInputException | InvalidGroupStateException | IOException | VerificationFailedException | SQLException e) {
          logger.error("error refreshing group {}", group.getIdString());
          Sentry.captureException(e);
        }
      }
      Database.Get().AccountDataTable.set(account.getACI(), IAccountDataTable.Key.LAST_ACCOUNT_REPAIR, ACCOUNT_REPAIR_VERSION_REFRESH_ALL_GROUPS);
    } catch (NoSuchAccountException | ServerNotFoundException | IOException | InvalidProxyException | SQLException e) {
      logger.error("error repairing groups for account {}", account.getACI().toString());
      Sentry.captureException(e);
    }
  }

  private static void clearContactsTableIfNonPrimaryDevice(Account account) throws SQLException {
    // primary device can't get contacts via sync from other devices, so dont wipe out the contacts table
    if (account.getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
      logger.info("clearing local contact list to fix potential data corruption. A contact sync from the primary device will be requested");
      account.getDB().ContactsTable.clear();
      BackgroundJobRunnerThread.queue(new SendSyncRequestJob(account, SignalServiceProtos.SyncMessage.Request.Type.CONTACTS));
    }

    Database.Get().AccountDataTable.set(account.getACI(), IAccountDataTable.Key.LAST_ACCOUNT_REPAIR, ACCOUNT_REPAIR_VERSION_CLEAR_CONTACTS_TABLE);
  }
}
