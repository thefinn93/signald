package io.finn.signald.util;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;

// TODO: Consider deleting this class
public class UnidentifiedAccessUtil {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;

  public UnidentifiedAccessUtil(Account account) { this.account = account; }

  public List<UnidentifiedAccess> getAccessFor(Collection<Recipient> recipients)
      throws NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidCertificateException, SQLException, IOException, InvalidKeyException {
    List<UnidentifiedAccess> result = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      result.add(getAccessFor(recipient));
    }
    return result;
  }

  public UnidentifiedAccess getAccessFor(Recipient recipient)
      throws NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidCertificateException, SQLException, IOException, InvalidKeyException {
    Manager m = Manager.get(account.getACI());
    ProfileAndCredentialEntry recipientProfileKeyCredential = m.getAccountData().profileCredentialStore.get(recipient);
    if (recipientProfileKeyCredential == null) {
      return null;
    }

    byte[] recipientUnidentifiedAccessKey = recipientProfileKeyCredential.getUnidentifiedAccessKey();

    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

    return new UnidentifiedAccess(recipientUnidentifiedAccessKey, selfUnidentifiedAccessCertificate);
  }

  private byte[] getSenderCertificate() throws NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    try {
      long lastRefresh = account.getLastSenderCertificateRefreshTime();
      byte[] cert;
      if (System.currentTimeMillis() - lastRefresh > TimeUnit.DAYS.toMillis(1)) {
        logger.debug("refreshing unidentified access sender certificate");
        cert = account.getSignalDependencies().getAccountManager().getSenderCertificateForPhoneNumberPrivacy();
        account.setSenderCertificate(cert);
        account.setSenderCertificateRefreshTimeNow();
      } else {
        cert = account.getSenderCertificate();
      }
      return cert;
    } catch (IOException | SQLException e) {
      logger.warn("Failed to get sealed sender certificate, ignoring: {}", e.getMessage());
      return null;
    }
  }
}
