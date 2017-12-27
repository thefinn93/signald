package io.finn.signald;

import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.internal.util.Base64;

import org.asamk.signal.storage.groups.GroupInfo;

import java.util.List;

class JsonGroupInfo {
    String groupId;
    List<String> members;
    String name;
    String type;

    JsonGroupInfo(SignalServiceGroup groupInfo, Manager m) {
        this.groupId = Base64.encodeBytes(groupInfo.getGroupId());
        if (groupInfo.getMembers().isPresent()) {
            this.members = groupInfo.getMembers().get();
        }
        if (groupInfo.getName().isPresent()) {
            this.name = groupInfo.getName().get();
        } else if( m != null) {
            GroupInfo group = m.getGroup(groupInfo.getGroupId());
            if( group != null)
                this.name = group.name;
        }

        this.type = groupInfo.getType().toString();
    }
}
