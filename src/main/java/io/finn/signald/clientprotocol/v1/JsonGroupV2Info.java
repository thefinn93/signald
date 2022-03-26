/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.Account;
import io.finn.signald.GroupInviteLinkUrl;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IGroupsTable;
import io.finn.signald.util.GroupsUtil;
import io.reactivex.rxjava3.annotations.NonNull;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.storageservice.protos.groups.AccessControl;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

@Doc("Information about a Signal group")
public class JsonGroupV2Info {
  private static final Logger logger = LogManager.getLogger();

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
  @Doc("indicates if the group is an announcements group. Only admins are allowed to send messages to announcements groups. Options are UNKNOWN, ENABLED or DISABLED")
  public String announcements;

  @Doc("will be set to true for incoming messages to indicate the user has been removed from the group") public boolean removed;

  @JsonProperty("group_change")
  @Doc("Represents a peer-to-peer group change done by a user. Will not be set if the group change signature fails "
       + "verification. This is usually only set inside of incoming messages.")
  public GroupChange groupChange;

  public JsonGroupV2Info() {}

  public JsonGroupV2Info(JsonGroupV2Info o) {
    id = o.id;
    masterKey = o.masterKey;
    removed = o.removed;
    update(o);
  }

  public JsonGroupV2Info(IGroupsTable.IGroup g) { this(g.getSignalServiceGroupV2(), g.getDecryptedGroup()); }

  // format a group for an incoming message
  public JsonGroupV2Info(SignalServiceGroupV2 group, ACI aci) {
    masterKey = Base64.encodeBytes(group.getMasterKey().serialize());
    id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(group.getMasterKey()).serialize());
    revision = group.getRevision();

    Optional<IGroupsTable.IGroup> localState;
    try {
      localState = Database.Get(aci).GroupsTable.get(group);
    } catch (InvalidProtocolBufferException | InvalidInputException | SQLException e) {
      logger.error("error fetching group state from local db");
      Sentry.captureException(e);
      return;
    }

    if (localState.isPresent() && group.hasSignedGroupChange()) {
      try {
        groupChange = GroupChange.fromBytes(Common.getGroups(new Account(aci)), localState.get(), group.getSignedGroupChange());
      } catch (InvalidGroupStateException | InternalError | ServerNotFoundError | NoSuchAccountError | InvalidProxyError | IOException e) {
        logger.error("error decrypting and serializing signed group change");
        Sentry.captureException(e);
      } catch (VerificationFailedException e) {
        logger.warn("unable to verify supplied group change");
        Sentry.captureException(e);
      }
    }

    removed = !localState.isPresent();
  }

  public JsonGroupV2Info(GroupMasterKey masterKey, @NonNull DecryptedGroup decryptedGroup) {
    this.masterKey = Base64.encodeBytes(masterKey.serialize());
    id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(masterKey).serialize());
    revision = decryptedGroup.getRevision();
    initializeWithDecryptedGroup(masterKey, decryptedGroup);
  }

  public JsonGroupV2Info(SignalServiceGroupV2 signalServiceGroupV2, DecryptedGroup decryptedGroup) {
    masterKey = Base64.encodeBytes(signalServiceGroupV2.getMasterKey().serialize());
    id = Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(signalServiceGroupV2.getMasterKey()).serialize());
    revision = signalServiceGroupV2.getRevision();

    if (decryptedGroup != null) {
      if (signalServiceGroupV2.getRevision() != decryptedGroup.getRevision()) {
        logger.warn("group revision mismatch for group " + id + "("
                    + "signalServiceGroupV2: " + signalServiceGroupV2.getRevision() + ", "
                    + "decryptedGroup: " + decryptedGroup.getRevision());
      }

      initializeWithDecryptedGroup(signalServiceGroupV2.getMasterKey(), decryptedGroup);
    }
  }

  private void initializeWithDecryptedGroup(@NonNull GroupMasterKey masterKey, @NonNull DecryptedGroup decryptedGroup) {
    title = decryptedGroup.getTitle();
    description = decryptedGroup.getDescription();
    timer = decryptedGroup.getDisappearingMessagesTimer().getDuration();
    members = new ArrayList<>();
    members = decryptedGroup.getMembersList().stream().map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e))).collect(Collectors.toList());
    pendingMembers = decryptedGroup.getPendingMembersList().stream().map(e -> new JsonAddress(DecryptedGroupUtil.toUuid(e))).collect(Collectors.toList());
    requestingMembers = decryptedGroup.getRequestingMembersList().stream().map(e -> new JsonAddress(UuidUtil.fromByteStringOrUnknown(e.getUuid()))).collect(Collectors.toList());

    AccessControl.AccessRequired access = decryptedGroup.getAccessControl().getAddFromInviteLink();
    if (access == AccessControl.AccessRequired.ANY || access == AccessControl.AccessRequired.ADMINISTRATOR) {
      inviteLink = GroupInviteLinkUrl.forGroup(masterKey, decryptedGroup).getUrl();
    }

    accessControl = new GroupAccessControl(decryptedGroup.getAccessControl());

    memberDetail = decryptedGroup.getMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    pendingMemberDetail = decryptedGroup.getPendingMembersList().stream().map(GroupMember::new).collect(Collectors.toList());
    announcements = decryptedGroup.getIsAnnouncementGroup().name();
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
    announcements = other.announcements;

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

    // note: this doesn't make a copy
    groupChange = other.groupChange;
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
