package io.finn.signald.db;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.whispersystems.signalservice.api.push.ACI;
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
  void deleteAccount(ACI aci) throws SQLException;
  void deleteSharedWith(Recipient source) throws SQLException;
}
