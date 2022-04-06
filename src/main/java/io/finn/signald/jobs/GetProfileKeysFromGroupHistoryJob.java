package io.finn.signald.jobs;

import io.finn.signald.Groups;
import io.finn.signald.Manager;
import io.finn.signald.db.IGroupsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.util.Base64;

public class GetProfileKeysFromGroupHistoryJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final ACI aci;
  private final GroupSecretParams groupSecretParams;
  private final int logsNeededFromRevision;
  private final int mostRecentGroupRevision;

  public GetProfileKeysFromGroupHistoryJob(@NotNull ACI aci, @NotNull GroupSecretParams groupSecretParams, int logsNeededFromRevision, int mostRecentGroupRevision) {
    this.aci = aci;
    this.groupSecretParams = groupSecretParams;
    this.logsNeededFromRevision = logsNeededFromRevision;
    this.mostRecentGroupRevision = mostRecentGroupRevision;
  }

  @Override
  public void run() throws InvalidInputException, InvalidGroupStateException, SQLException, IOException, VerificationFailedException, NoSuchAccountException,
                           ServerNotFoundException, InvalidProxyException, InvalidKeyException {
    final Manager m = Manager.get(aci);
    final Groups groups = m.getAccount().getGroups();
    // don't refresh group from server, and ensure we're still in the group
    final Optional<IGroupsTable.IGroup> localGroup = groups.getGroup(groupSecretParams, mostRecentGroupRevision);
    final String groupId = Base64.encodeBytes(groupSecretParams.getPublicParams().getGroupIdentifier().serialize());
    if (localGroup.isEmpty()) {
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
