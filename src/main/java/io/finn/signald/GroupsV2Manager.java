/*
 * Copyright (C) 2020 Finn Herzfeld
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

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.storage.GroupsV2Storage;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GroupsV2Manager {
    private final GroupsV2Api groupsV2Api;
    private final GroupsV2Storage storage;

    public GroupsV2Manager(GroupsV2Api groupsV2Api, GroupsV2Storage storage) {
        this.groupsV2Api = groupsV2Api;
        this.storage = storage;
    }

    public boolean handleIncomingDataMessage(SignalServiceDataMessage message, UUID uuid) throws IOException, VerificationFailedException, InvalidGroupStateException {
        assert message.getGroupContext().isPresent();
        assert message.getGroupContext().get().getGroupV2().isPresent();
        SignalServiceGroupV2 group = message.getGroupContext().get().getGroupV2().get();
        JsonGroupV2Info localState = storage.get(group);

        if(localState != null || localState.revision < group.getRevision()) {
            int today = (int) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
            GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
            AuthCredentialResponse authCredential = storage.getAuthCredential(groupsV2Api, today);
            GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(uuid, today, groupSecretParams, authCredential);
            JsonGroupV2Info newState = new JsonGroupV2Info(group, groupsV2Api.getGroup(groupSecretParams, authorization));
            if(localState == null) {
                storage.groups.add(newState);
            } else {
                localState.update(newState);
            }
            return true;
        }
        return false;
    }

    public JsonGroupV2Info getGroup(SignalServiceGroupV2 g) {
        return storage.get(g);
    }
}
