/*
 * Copyright (C) 2021 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald.util;

import io.finn.signald.BuildConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.kdf.HKDFv3;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;

public class GroupsUtil {
  private final static Logger logger = LogManager.getLogger();

  public static GroupsV2Operations GetGroupsV2Operations(SignalServiceConfiguration serviceConfiguration) {
    GroupsV2Operations groupsV2Operations;
    try {
      groupsV2Operations = new GroupsV2Operations(ClientZkOperations.create(serviceConfiguration));
    } catch (Throwable ignored) {
      groupsV2Operations = null;
      logger.warn(
          "Unable to load groups v2 library. likely due to being non-linux or non-x86. See https://gitlab.com/signald/signald/-/issues/85. ANOTHER NATIVE LIBRARY IS BECOMING MANDATORY SOON AND SIGNALD WILL NOT RUN ON THIS SYSTEM! Please open an issue so we can address it before then: " +
          BuildConfig.ERROR_REPORTING_URL);
    }
    return groupsV2Operations;
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

  public static SignalServiceAddress getMemberAddress(DecryptedMember member) { return new SignalServiceAddress(UuidUtil.fromByteString(member.getUuid())); }
}
