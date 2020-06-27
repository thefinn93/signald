package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
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

    public void addMembers(List<SignalServiceAddress> newMembers) {
        for(SignalServiceAddress m : newMembers) {
            members.add(new JsonAddress(m));
        }
    }

    public void setMembers(List<String> membersNumbers) {
        for(String m : membersNumbers) {
            members.add(new JsonAddress(m));
        }
    }

    @JsonProperty
    public boolean active;

    public GroupInfo(byte[] groupId) {
        this.groupId = groupId;
    }

    // Constructor required for creation from JSON
    public GroupInfo(@JsonProperty("groupId") byte[] groupId, @JsonProperty("name") String name, @JsonProperty("members") ArrayList<JsonAddress> members, @JsonProperty("avatarId") long avatarId) {
        this.groupId = groupId;
        this.name = name;
        this.members = members;
        this.avatarId = avatarId;
    }

    public void removeMember(SignalServiceAddress source) {
        this.members.remove(new JsonAddress(source));
    }
}
