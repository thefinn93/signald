package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import org.apache.logging.log4j.LogManager;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.ArrayList;
import java.util.List;

public class GroupInfo {
    @JsonProperty
    public final byte[] groupId;

    @JsonProperty
    public String name;

    @JsonProperty
    public List<JsonAddress> members = new ArrayList<>();

    private long avatarId;

    @JsonIgnore
    public long getAvatarId() {
        return avatarId;
    }

    @JsonIgnore
    public List<SignalServiceAddress> getMembers() {
        List<SignalServiceAddress> l = new ArrayList<>();
        for(JsonAddress m : members) {
            l.add(m.getSignalServiceAddress());
        }
        return l;
    }

    public boolean isMember(JsonAddress address) {
        for(JsonAddress member : members) {
            if(member.equals(address)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMember(SignalServiceAddress address) {
        return isMember(new JsonAddress(address));
    }

    public void addMembers(List<SignalServiceAddress> newMembers) {
        for(SignalServiceAddress m : newMembers) {
            addMember(m);
        }
    }

    public void addMember(SignalServiceAddress member) {
        addMember(new JsonAddress(member));
    }

    public void addMember(JsonAddress member) {
        LogManager.getLogger("GroupInfo").debug("adding member " + member.toRedactedString() + " to " + groupId);
        if(!isMember(member)) {
            members.add(member);
        }
    }

    @JsonProperty
    public boolean active;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    public GroupInfo(DeviceGroup g) {
        groupId = g.getId();
        if(g.getName().isPresent()) {
            name = g.getName().get();
        }
        addMembers(g.getMembers());
        // TODO: Avatar support
    }


    // Constructor required for creation from JSON
    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") List<JsonAddress> members, @JsonProperty("avatarId") long avatarId) {
        this.groupId = groupId;
        this.name = name;
        for(JsonAddress member : members) {
            addMember(member);
        }
        this.avatarId = avatarId;
    }

    public void removeMember(SignalServiceAddress source) {
        this.members.remove(new JsonAddress(source));
    }

    public String toString() {
        return name + " (" + groupId + ")";
    }
}
