package io.finn.signald.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface IIdentityKeysTable extends IdentityKeyStore {
  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String IDENTITY_KEY = "identity_key";
  String TRUST_LEVEL = "trust_level";
  String ADDED = "added";
  boolean saveIdentity(String address, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, SQLException;
  boolean saveIdentity(Recipient recipient, IdentityKey identityKey, TrustLevel trustLevel, Date added);
  List<IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException;
  List<IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException;
  void deleteAccount(ACI aci) throws SQLException;
  void trustAllKeys() throws SQLException;

  default boolean saveIdentity(Recipient recipient, IdentityKey identityKey, TrustLevel trustLevel) { return saveIdentity(recipient, identityKey, trustLevel, new Date()); }

  class IdentityKeyRow {
    SignalServiceAddress address;
    IdentityKey identityKey;
    TrustLevel trustLevel;
    Date added;

    public IdentityKeyRow(SignalServiceAddress address, IdentityKey identityKey, TrustLevel trustLevel, Date added) {
      this.address = address;
      this.identityKey = identityKey;
      this.trustLevel = trustLevel;
      this.added = added;
    }

    boolean isTrusted() { return trustLevel == TrustLevel.TRUSTED_UNVERIFIED || trustLevel == TrustLevel.TRUSTED_VERIFIED; }

    public IdentityKey getKey() { return this.identityKey; }

    public TrustLevel getTrustLevel() { return this.trustLevel; }

    public Date getDateAdded() { return this.added; }

    public byte[] getFingerprint() { return identityKey.getPublicKey().serialize(); }

    public SignalServiceAddress getAddress() { return address; }

    public String getTrustLevelString() { return Objects.requireNonNullElse(trustLevel, TrustLevel.TRUSTED_UNVERIFIED).name(); }

    public long getAddedTimestamp() { return added == null ? 0 : added.getTime(); }
  }
}
