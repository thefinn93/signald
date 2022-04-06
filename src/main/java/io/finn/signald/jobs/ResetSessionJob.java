package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;

public class ResetSessionJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;
  private final Recipient recipient;

  public ResetSessionJob(Account account, Recipient recipient) {
    this.account = account;
    this.recipient = recipient;
  }

  @Override
  public void run() throws SQLException, IOException, NoSuchAccountException, ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    logger.info("resetting session with {}", recipient.toRedactedString());
    logger.debug("archiving all sessions with {}", recipient.toRedactedString());
    account.getProtocolStore().archiveAllSessions(recipient);
    if (!recipient.equals(account.getSelf())) {
      logger.debug("sending null message back");
      Optional<UnidentifiedAccessPair> unidentifiedAccessPair = Manager.get(account.getACI()).getAccessPairFor(recipient);
      try {
        account.getSignalDependencies().getMessageSender().sendNullMessage(recipient.getAddress(), unidentifiedAccessPair);
      } catch (UntrustedIdentityException e) {
        account.getProtocolStore().handleUntrustedIdentityException(e);
      }
    }
  }
}
