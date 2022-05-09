/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.LegacyGroup;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;

public interface IGroupsTable {
  String ROWID = "rowid";
  String ACCOUNT_UUID = "account_uuid";
  String GROUP_ID = "group_id";
  String MASTER_KEY = "master_key";
  String REVISION = "revision";
  String LAST_AVATAR_FETCH = "last_avatar_fetch";
  String DISTRIBUTION_ID = "distribution_id";
  String GROUP_INFO = "group_info";

  Optional<IGroup> get(GroupIdentifier identifier) throws SQLException, InvalidInputException, InvalidProtocolBufferException;
  List<IGroup> getAll() throws SQLException;
  File getGroupAvatarFile(GroupIdentifier groupId);
  void deleteAccount(ACI aci) throws SQLException;
  void setGroupAvatarPath(String path) throws IOException;

  default Optional<IGroup> get(SignalServiceGroupV2 group) throws InvalidProtocolBufferException, InvalidInputException, SQLException {
    return get(GroupSecretParams.deriveFromMasterKey(group.getMasterKey()).getPublicParams().getGroupIdentifier());
  }

  void upsert(GroupMasterKey masterKey, DecryptedGroup decryptedGroup, DistributionId distributionId, int lastAvatarFetch)
      throws SQLException, InvalidInputException, InvalidProtocolBufferException;
  default void upsert(GroupMasterKey masterKey, DecryptedGroup decryptedGroup) throws SQLException, InvalidInputException, InvalidProtocolBufferException {
    upsert(masterKey, decryptedGroup, null, -1);
  }

  @Deprecated
  default void upsert(LegacyGroup groupFromLegacyStorage) throws SQLException, InvalidInputException, InvalidProtocolBufferException {
    upsert(groupFromLegacyStorage.getMasterKey(), groupFromLegacyStorage.getGroup(), groupFromLegacyStorage.getDistributionId(), groupFromLegacyStorage.getLastAvatarFetch());
  }

  interface IGroup {
    void setDecryptedGroup(DecryptedGroup decryptedGroup) throws SQLException;
    void delete() throws SQLException;
    DistributionId getDistributionId();
    DistributionId getOrCreateDistributionId() throws SQLException;
    GroupIdentifier getId();
    String getIdString();
    int getRevision();
    GroupMasterKey getMasterKey();
    GroupSecretParams getSecretParams();
    DecryptedGroup getDecryptedGroup();
    SignalServiceGroupV2 getSignalServiceGroupV2();
    JsonGroupV2Info getJsonGroupV2Info();
    List<Recipient> getMembers() throws IOException, SQLException;
    List<Recipient> getPendingMembers() throws IOException, SQLException;
    List<Recipient> getRequestingMembers() throws IOException, SQLException;
    boolean isAdmin(Recipient recipient);
  }
}
