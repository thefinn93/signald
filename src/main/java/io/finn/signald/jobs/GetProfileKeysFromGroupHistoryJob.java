package io.finn.signald.jobs;

import io.finn.signald.Groups;
import io.finn.signald.Manager;
import io.finn.signald.ProfileKeySet;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.util.Base64;

public class GetProfileKeysFromGroupHistoryJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final ACI aci;
  private final GroupSecretParams groupSecretParams;
  private final int logsNeededFromRevision;
  private final int mostRecentGroupRevision;
  /** Whether this group is new to signald */
  private final boolean isNewGroup;

  public GetProfileKeysFromGroupHistoryJob(@NotNull ACI aci, @NotNull GroupSecretParams groupSecretParams, int logsNeededFromRevision, int mostRecentGroupRevision,
                                           boolean isNewGroup) {
    this.aci = aci;
    this.groupSecretParams = groupSecretParams;
    this.logsNeededFromRevision = logsNeededFromRevision;
    this.mostRecentGroupRevision = mostRecentGroupRevision;
    this.isNewGroup = isNewGroup;
  }

  @Override
  public void run() throws InvalidInputException, InvalidGroupStateException, SQLException, IOException, VerificationFailedException, NoSuchAccountException,
                           ServerNotFoundException, InvalidProxyException, InvalidKeyException {
    final Manager m = Manager.get(aci);
    final Groups groups = m.getAccount().getGroups();
    // don't refresh group from server, and ensure we're still in the group
    final Optional<GroupsTable.Group> localGroup = groups.getGroup(groupSecretParams, mostRecentGroupRevision);
    final String groupId = Base64.encodeBytes(groupSecretParams.getPublicParams().getGroupIdentifier().serialize());
    if (!localGroup.isPresent()) {
      logger.warn("Missing group " + groupId + "; might've left the group");
      return;
    }
    if (logsNeededFromRevision < 0) {
      logger.warn("logsNeededFromRevision should be nonnegative, but is " + logsNeededFromRevision);
      return;
    }

    final var firstPage = groups.getGroupHistoryPage(groupSecretParams, logsNeededFromRevision, false);
    logger.info("Requesting from server logs for group " + groupId + ", starting from revision " + logsNeededFromRevision);
    groups.persistProfileKeysFromServerGroupHistory(groupSecretParams, firstPage, localGroup.get());
  }
}
