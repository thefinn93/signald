/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

import io.finn.signald.ServiceConfig;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.libsignal.zkgroup.groups.GroupMasterKey;
import org.signal.libsignal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class GroupsUtil {

  public static GroupsV2Operations GetGroupsV2Operations(SignalServiceConfiguration serviceConfiguration) {
    return new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration), ServiceConfig.GROUP_MAX_SIZE);
  }

  public static GroupIdentifier GetIdentifierFromMasterKey(GroupMasterKey masterKey) {
    return GroupSecretParams.deriveFromMasterKey(masterKey).getPublicParams().getGroupIdentifier();
  }

  public static byte[] getGroupId(GroupMasterKey groupMasterKey) {
    final GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    return groupSecretParams.getPublicParams().getGroupIdentifier().serialize();
  }
}
