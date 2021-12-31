/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class GroupsUtil {

  public static GroupsV2Operations GetGroupsV2Operations(SignalServiceConfiguration serviceConfiguration) {
    return new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration));
  }

  public static GroupIdentifier GetIdentifierFromMasterKey(GroupMasterKey masterKey) {
    return GroupSecretParams.deriveFromMasterKey(masterKey).getPublicParams().getGroupIdentifier();
  }

  public static byte[] getGroupId(GroupMasterKey groupMasterKey) {
    final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    return groupSecretParams.getPublicParams().getGroupIdentifier().serialize();
  }

  public static GroupMasterKey deriveV2MigrationMasterKey(byte[] groupId) {
    try {
      return new GroupMasterKey(new HKDFv3().deriveSecrets(groupId, "GV2 Migration".getBytes(), GroupMasterKey.SIZE));
    } catch (InvalidInputException e) {
      throw new AssertionError(e);
    }
  }
}
