package io.finn.signald.db;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.DistributionId;

public interface ISenderKeySharedTable {
  String ACCOUNT_UUID = "account_uuid";
  String DISTRIBUTION_ID = "distribution_id";
  String DEVICE = "device";
  String ADDRESS = "address";

  Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId);
  void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses);
  void clearSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses);
  void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses);
  boolean isMultiDevice();
  void deleteAllFor(DistributionId distributionId) throws SQLException;
  void deleteForAll(Recipient recipient) throws SQLException;
  void deleteAccount(UUID uuid) throws SQLException;
  void deleteSharedWith(Recipient source) throws SQLException;
}
