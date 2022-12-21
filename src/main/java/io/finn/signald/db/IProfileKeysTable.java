package io.finn.signald.db;

import io.finn.signald.Account;
import io.finn.signald.ProfileKeySet;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.SyncStorageDataJob;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.ACI;

public interface IProfileKeysTable {
  byte[] UNRESTRICTED_KEY = new byte[16];

  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String PROFILE_KEY = "profile_key";
  String PROFILE_KEY_CREDENTIAL = "profile_key_credential";
  String REQUEST_PENDING = "request_pending";
  String UNIDENTIFIED_ACCESS_MODE = "unidentified_access_mode";

  ProfileKey getProfileKey(Recipient recipient) throws SQLException;
  void setProfileKey(Recipient recipient, ProfileKey profileKey) throws SQLException;

  ExpiringProfileKeyCredential getExpiringProfileKeyCredential(Recipient recipient) throws SQLException, InvalidInputException;
  void setExpiringProfileKeyCredential(Recipient recipient, ExpiringProfileKeyCredential profileKeyCredential) throws SQLException;

  boolean isRequestPending(Recipient recipient) throws SQLException;
  void setRequestPending(Recipient recipient, boolean isRequestPending) throws SQLException;

  UnidentifiedAccessMode getUnidentifiedAccessMode(Recipient recipient) throws SQLException;
  void setUnidentifiedAccessMode(Recipient recipient, UnidentifiedAccessMode mode) throws SQLException;
  default byte[] getUnidentifiedAccessKey(Recipient recipient) throws SQLException {
    UnidentifiedAccessMode mode = getUnidentifiedAccessMode(recipient);
    switch (mode) {
    case DISABLED:
      return null;
    case UNRESTRICTED:
      return UNRESTRICTED_KEY;
    case UNKNOWN:
    case ENABLED:
      ProfileKey profileKey = getProfileKey(recipient);
      if (profileKey == null) {
        return mode == UnidentifiedAccessMode.UNKNOWN ? UNRESTRICTED_KEY : null;
      } else {
        return UnidentifiedAccess.deriveAccessKeyFrom(profileKey);
      }
    default:
      throw new AssertionError("Unknown mode: " + mode);
    }
  }

  /**
   * Persists the given profile key set into the credential store. This method respects authoritative profile keys.
   *
   * @param profileKeySet A set of profile keys to persist, typically from an incoming group update.
   * @return The profile entries that were actually updated. Only authoritative profile keys can be used to update
   * profile keys for users that we already have, so this may be less than the actual number of keys in the input.
   */
  default Set<Recipient> persistProfileKeySet(ProfileKeySet profileKeySet) throws SQLException, IOException {
    Logger logger = LogManager.getLogger();

    final Map<Recipient, ProfileKey> profileKeys = profileKeySet.getProfileKeys();
    final Map<Recipient, ProfileKey> authoritativeProfileKeys = profileKeySet.getAuthoritativeProfileKeys();
    final int totalKeys = profileKeys.size() + authoritativeProfileKeys.size();
    if (totalKeys == 0) {
      return Collections.emptySet();
    }

    logger.info("Persisting {} profile keys, {} of which are authoritative", totalKeys, authoritativeProfileKeys.size());

    final var updated = new HashSet<Recipient>(totalKeys);

    for (Map.Entry<Recipient, ProfileKey> entry : profileKeys.entrySet()) {
      ProfileKey current = getProfileKey(entry.getKey());
      if (current == null) {
        logger.debug("Learned new profile key for " + entry.getKey().toRedactedString());
        setProfileKey(entry.getKey(), entry.getValue());
        updated.add(entry.getKey());
      }
    }

    for (Map.Entry<Recipient, ProfileKey> entry : authoritativeProfileKeys.entrySet()) {
      final var thisAddress = entry.getKey().getAddress();
      Recipient self = profileKeySet.getSelf();
      if (self.getAddress().matches(thisAddress)) {
        logger.info("Seen authoritative update for self");
        final var selfProfileKey = getProfileKey(self);
        if (selfProfileKey != null && !selfProfileKey.equals(entry.getValue())) {
          logger.warn("Seen authoritative update for self that didn't match local, scheduling storage sync");
          BackgroundJobRunnerThread.queue(new SyncStorageDataJob(new Account(ACI.from(self.getServiceId().uuid()))));
        }
      } else {
        logger.debug("Profile key from owner for " + entry.getKey().toRedactedString());
        setProfileKey(entry.getKey(), entry.getValue());
        updated.add(entry.getKey());
      }
    }

    return updated;
  }

  enum UnidentifiedAccessMode {
    UNKNOWN(0),
    DISABLED(1),
    ENABLED(2),
    UNRESTRICTED(3);

    private final int mode;

    UnidentifiedAccessMode(int mode) { this.mode = mode; }

    public int getMode() { return mode; }

    public static UnidentifiedAccessMode fromMode(int mode) { return values()[mode]; }
  }
}
