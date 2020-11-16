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

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.storage.AddressResolver;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.util.Base64;

import java.util.List;
import java.util.stream.Collectors;

public class JsonGroupV2Info {
    public String id;
    public String masterKey;
    public int revision;

    // Fields from DecryptedGroup
    public String title;
    public Integer timer;
    public List<JsonAddress> members;
    public List<JsonAddress> pendingMembers;
    public List<JsonAddress> requestingMembers;
    public String inviteLinkPassword;

    public JsonGroupV2Info() {}

    public JsonGroupV2Info(SignalServiceGroupV2 signalServiceGroupV2, DecryptedGroup decryptedGroup) {
        if(signalServiceGroupV2 != null) {
            masterKey = Base64.encodeBytes(signalServiceGroupV2.getMasterKey().serialize());
            id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(signalServiceGroupV2.getMasterKey()).serialize());
            revision = signalServiceGroupV2.getRevision();
        }

        if(decryptedGroup != null) {
            title = decryptedGroup.getTitle();
            timer = decryptedGroup.getDisappearingMessagesTimer().getDuration();
            members = decryptedGroup.getMembersList().stream()
                    .map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e)))
                    .collect(Collectors.toList());
            pendingMembers = decryptedGroup.getPendingMembersList().stream()
                    .map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e)))
                    .collect(Collectors.toList());
//            requestingMembers = decryptedGroup.getRequestingMembersList().stream()
//                    .map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e.getUuid())))
//                    .collect(Collectors.toList());
//            inviteLinkPassword = decryptedGroup.getInviteLinkPassword();
        }
    }

    public void update(JsonGroupV2Info other) {
        assert id.equals(other.id);
        assert masterKey.equals(other.masterKey);
        revision = other.revision;
        title = other.title;
        timer = other.timer;
        members = other.members;
        pendingMembers = other.pendingMembers;
        requestingMembers = other.requestingMembers;
        inviteLinkPassword = other.inviteLinkPassword;
    }

    public void resolveMembers(AddressResolver resolver) {
        members.stream().map(e -> e.resolve(resolver)).collect(Collectors.toList());
        pendingMembers.stream().map(e -> e.resolve(resolver)).collect(Collectors.toList());
    }
}
