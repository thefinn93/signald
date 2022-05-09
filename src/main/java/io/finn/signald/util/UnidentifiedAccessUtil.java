package io.finn.signald.util;

import io.finn.signald.Account;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.push.ACI;

// TODO: Consider deleting this class
public class UnidentifiedAccessUtil {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;

  public UnidentifiedAccessUtil(ACI aci) { account = new Account(aci); }

  public List<UnidentifiedAccess> getAccessFor(Collection<Recipient> recipients)
      throws NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidCertificateException, SQLException, IOException, InvalidKeyException {
    List<UnidentifiedAccess> result = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      result.add(getAccessFor(recipient));
    }
    return result;
  }

  public UnidentifiedAccess getAccessFor(Recipient recipient)
      throws NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidCertificateException, SQLException {
    byte[] recipientUnidentifiedAccessKey = account.getDB().ProfileKeysTable.getUnidentifiedAccessKey(recipient);
    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();
    return new UnidentifiedAccess(recipientUnidentifiedAccessKey, selfUnidentifiedAccessCertificate);
  }

  public Optional<UnidentifiedAccess> getUnidentifiedAccess()
      throws NoSuchAccountException, SQLException, IOException, ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    ProfileKey ownProfileKey = account.getDB().ProfileKeysTable.getProfileKey(account.getSelf());
    byte[] selfUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(ownProfileKey);
    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

    try {
      return Optional.of(new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate));
    } catch (InvalidCertificateException e) {
      return Optional.empty();
    }
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

  public List<Optional<UnidentifiedAccessPair>> getAccessPairFor(Collection<Recipient> recipients)
      throws NoSuchAccountException, SQLException, IOException, ServerNotFoundException, InvalidProxyException {
    List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      result.add(getAccessPairFor(recipient));
    }
    return result;
  }

  public Optional<UnidentifiedAccessPair> getAccessPairFor(Recipient recipient)
      throws SQLException, IOException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    Database db = account.getDB();

    ProfileKey selfProfileKey = db.ProfileKeysTable.getProfileKey(account.getSelf());
    if (selfProfileKey == null) {
      logger.debug("cannot get unidentified access: no profile key for own account");
      return Optional.empty();
    }

    byte[] selfUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(selfProfileKey);
    if (selfUnidentifiedAccessKey == null) {
      logger.debug("cannot get unidentified access: no unidentified access key for own account");
      return Optional.empty();
    }

    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();
    if (selfUnidentifiedAccessCertificate == null) {
      logger.debug("cannot get unidentified access: no unidentified access certificate for own account");
      return Optional.empty();
    }

    byte[] recipientUnidentifiedAccessKey = db.ProfileKeysTable.getUnidentifiedAccessKey(recipient);
    if (recipientUnidentifiedAccessKey == null) {
      logger.debug("cannot get unidentified access: no unidentified access key for recipient");
      return Optional.empty();
    }

    try {
      return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(recipientUnidentifiedAccessKey, selfUnidentifiedAccessCertificate),
                                                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
    } catch (InvalidCertificateException e) {
      logger.debug("cannot get unidentififed access: ", e);
      return Optional.empty();
    }
  }
}
