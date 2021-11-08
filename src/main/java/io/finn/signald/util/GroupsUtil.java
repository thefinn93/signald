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
