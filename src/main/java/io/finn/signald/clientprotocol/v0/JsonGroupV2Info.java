/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.GroupInviteLinkUrl;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

@Deprecated(1641027661)
public class JsonGroupV2Info {
  @ExampleValue(ExampleValue.GROUP_ID) public String id;
  public String masterKey;
  @ExampleValue("5") public int revision;

  // Fields from DecryptedGroup
  @ExampleValue(ExampleValue.GROUP_TITLE) public String title;
  public String description;
  @ExampleValue(ExampleValue.LOCAL_GROUP_AVATAR_PATH) @Doc("path to the group's avatar on local disk, if available") public String avatar;
  @ExampleValue("604800") public int timer;

  @JsonProperty public List<JsonAddress> members;
  public List<JsonAddress> pendingMembers;
  public List<JsonAddress> requestingMembers;
  @Doc("the signal.group link, if applicable") public String inviteLink;
  @Doc("current access control settings for this group") public GroupAccessControl accessControl;

  @Doc("detailed member list") public List<GroupMember> memberDetail;
  @Doc("detailed pending member list") public List<GroupMember> pendingMemberDetail;

  public JsonGroupV2Info() {}

  public JsonGroupV2Info(JsonGroupV2Info o) {
    id = o.id;
    masterKey = o.masterKey;
    update(o);
  }

  public JsonGroupV2Info(SignalServiceGroupV2 signalServiceGroupV2, DecryptedGroup decryptedGroup) {
    masterKey = Base64.encodeBytes(signalServiceGroupV2.getMasterKey().serialize());
    id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(signalServiceGroupV2.getMasterKey()).serialize());
    revision = signalServiceGroupV2.getRevision();

    if (decryptedGroup != null) {
      title = decryptedGroup.getTitle();
      description = decryptedGroup.getDescription();
      timer = decryptedGroup.getDisappearingMessagesTimer().getDuration();
      members = new ArrayList<>();
      members = decryptedGroup.getMembersList().stream().map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e))).collect(Collectors.toList());
      pendingMembers = decryptedGroup.getPendingMembersList().stream().map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e))).collect(Collectors.toList());
      requestingMembers = decryptedGroup.getRequestingMembersList().stream().map(e -> new JsonAddress(UuidUtil.fromByteStringOrUnknown(e.getUuid()))).collect(Collectors.toList());

      AccessControl.AccessRequired access = decryptedGroup.getAccessControl().getAddFromInviteLink();
      if (access == AccessControl.AccessRequired.ANY || access == AccessControl.AccessRequired.ADMINISTRATOR) {
        inviteLink = GroupInviteLinkUrl.forGroup(signalServiceGroupV2.getMasterKey(), decryptedGroup).getUrl();
      }

      accessControl = new GroupAccessControl(decryptedGroup.getAccessControl());

      memberDetail = decryptedGroup.getMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
      pendingMemberDetail = decryptedGroup.getPendingMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    }
  }

  public void update(JsonGroupV2Info other) {
    if (!id.equals(other.id) || !masterKey.equals(other.masterKey)) {
      throw new IllegalArgumentException("IDs or master keys differ");
    }
    revision = other.revision;
    title = other.title;
    description = other.description;
    timer = other.timer;
    inviteLink = other.inviteLink;

    if (other.members != null) {
      members = new ArrayList<>();
      for (JsonAddress m : other.members) {
        members.add(new JsonAddress(m));
      }
    } else {
      members = null;
    }

    if (other.pendingMembers != null) {
      pendingMembers = new ArrayList<>();
      for (JsonAddress m : other.pendingMembers) {
        pendingMembers.add(new JsonAddress(m));
      }
    } else {
      pendingMembers = null;
    }

    if (other.requestingMembers != null) {
      requestingMembers = new ArrayList<>();
      for (JsonAddress m : other.requestingMembers) {
        requestingMembers.add(new JsonAddress(m));
      }
    } else {
      requestingMembers = null;
    }
  }

  public JsonGroupV2Info sanitized() {
    JsonGroupV2Info output = new JsonGroupV2Info(this);
    output.masterKey = null;
    return output;
  }

  @JsonIgnore
  public List<SignalServiceAddress> getMembers() {
    if (members == null) {
      return null;
    }
    List<SignalServiceAddress> l = new ArrayList<>();
    for (JsonAddress m : members) {
      l.add(m.getSignalServiceAddress());
    }
    return l;
  }

  @JsonIgnore
  public SignalServiceGroupV2 getSignalServiceGroupV2() throws IOException, InvalidInputException {
    GroupMasterKey groupMasterKey = new GroupMasterKey(Base64.decode(masterKey));
    return SignalServiceGroupV2.newBuilder(groupMasterKey).withRevision(revision).build();
  }

  @JsonIgnore
  public GroupMasterKey getMasterKey() throws IOException, InvalidInputException {
    return new GroupMasterKey(Base64.decode(masterKey));
  }
}
