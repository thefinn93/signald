/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Recipient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;

public class GroupInfo {
  @JsonProperty public byte[] groupId;

  @JsonProperty public String name;

  @JsonProperty public List<JsonAddress> members = new ArrayList<>();

  @JsonProperty public int messageExpirationTime;

  @JsonProperty public int inboxPosition;

  @JsonProperty public String color;

  @JsonProperty public boolean active;

  private long avatarId;

  @JsonIgnore
  public long getAvatarId() {
    return avatarId;
  }

  @JsonIgnore
  public List<SignalServiceAddress> getMembers() {
    List<SignalServiceAddress> l = new ArrayList<>();
    for (JsonAddress m : members) {
      if (m.uuid == null) {
        m.uuid = UuidUtil.UNKNOWN_UUID.toString();
      }
      l.add(m.getSignalServiceAddress());
    }
    return l;
  }

  public boolean isMember(JsonAddress address) {
    for (JsonAddress member : members) {
      if (member.matches(address)) {
        return true;
      }
    }
    return false;
  }

  public boolean isMember(SignalServiceAddress address) { return isMember(new JsonAddress(address)); }

  public void addMembers(Collection<SignalServiceAddress> newMembers) {
    for (SignalServiceAddress m : newMembers) {
      addMember(m);
    }
  }

  public void addMember(SignalServiceAddress member) { addMember(new JsonAddress(member)); }

  public void addMember(JsonAddress member) {
    if (!isMember(member)) {
      members.add(member);
    }
  }

  public GroupInfo() {}

  public GroupInfo(byte[] groupId) { this.groupId = groupId; }

  public GroupInfo(DeviceGroup g) {
    groupId = g.getId();
    if (g.getName().isPresent()) {
      name = g.getName().get();
    }
    addMembers(g.getMembers());

    // TODO: Avatar support

    if (g.getInboxPosition().isPresent()) {
      inboxPosition = g.getInboxPosition().get();
    }

    if (g.getColor().isPresent()) {
      color = g.getColor().get();
    }

    active = g.isActive();
  }

  public void removeMember(Recipient source) { this.members.remove(new JsonAddress(source.getAddress())); }

  public String toString() { return name + " (" + groupId + ")"; }
}
