/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import io.finn.signald.Manager;
import io.finn.signald.ServiceConfig;
import io.finn.signald.Util;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.util.GroupsUtil;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.*;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

@JsonSerialize(using = Group.GroupSerializer.class)
@JsonDeserialize(using = Group.GroupDeserializer.class)
public class Group {
  private static final Logger logger = LogManager.getLogger();

  public GroupMasterKey masterKey;
  public int revision;
  public DecryptedGroup group;
  private int lastAvatarFetch;
  private DistributionId distributionId;

  public Group(GroupMasterKey m, int r, DecryptedGroup d, DistributionId dist, int l) {
    masterKey = m;
    revision = r;
    group = d;
    distributionId = dist;
    lastAvatarFetch = l;
  }

  @JsonIgnore
  public List<SignalServiceAddress> getMembers() {
    if (group == null) {
      return null;
    }
    List<SignalServiceAddress> l = new ArrayList<>();
    for (DecryptedMember member : group.getMembersList()) {
      ACI aci = ACI.from(DecryptedGroupUtil.toUuid(member));
      l.add(new SignalServiceAddress(aci, Optional.absent()));
    }
    return l;
  }

  @JsonIgnore
  public List<SignalServiceAddress> getPendingMembers() {
    if (group == null) {
      return null;
    }
    List<SignalServiceAddress> l = new ArrayList<>();
    for (DecryptedPendingMember member : group.getPendingMembersList()) {
      ACI aci = ACI.from(DecryptedGroupUtil.toUuid(member));
      l.add(new SignalServiceAddress(aci, Optional.absent()));
    }
    return l;
  }

  public SignalServiceGroupV2 getSignalServiceGroupV2() {
    GroupMasterKey groupMasterKey = getMasterKey();
    return SignalServiceGroupV2.newBuilder(groupMasterKey).withRevision(revision).build();
  }

  public GroupMasterKey getMasterKey() { return masterKey; }

  public DecryptedGroup getGroup() { return group; }

  public boolean hasDistributionId() { return distributionId != null; }

  public void generateDistributionId() {
    if (distributionId == null) {
      distributionId = DistributionId.create();
    }
  }

  public DistributionId getDistributionId() { return distributionId; }

  public boolean isPendingMember(UUID query) {
    for (UUID m : DecryptedGroupUtil.pendingToUuidList(group.getPendingMembersList())) {
      if (m.equals(query)) {
        return true;
      }
    }
    return false;
  }

  public int getTimer() {
    if (group == null) {
      return 0;
    }
    return group.getDisappearingMessagesTimer().getDuration();
  }

  public JsonGroupV2Info getJsonGroupV2Info(Manager m) {
    try {
      fetchAvatar(m);
    } catch (IOException e) {
      logger.warn("Failed to fetch group avatar:" + e.getMessage());
    }
    JsonGroupV2Info jsonGroupV2Info = new JsonGroupV2Info(SignalServiceGroupV2.newBuilder(masterKey).withRevision(revision).build(), group);
    File avatarFile = m.getGroupAvatarFile(getGroupID());
    if (avatarFile.exists()) {
      jsonGroupV2Info.avatar = avatarFile.getAbsolutePath();
    }
    return jsonGroupV2Info;
  }

  public String getID() { return Base64.encodeBytes(GroupsUtil.GetIdentifierFromMasterKey(masterKey).serialize()); }

  public void update(Group g) {
    this.masterKey = g.masterKey;
    this.revision = g.revision;
    this.group = g.group;
  }

  public void update(Pair<DecryptedGroup, GroupChange> groupChangePair) {
    if (groupChangePair.first().getRevision() > revision) {
      revision = groupChangePair.first().getRevision();
      group = groupChangePair.first();
    }
  }

  private void fetchAvatar(Manager m) throws IOException {
    File avatarFile = m.getGroupAvatarFile(getGroupID());
    if (lastAvatarFetch == revision) {
      // group avatar has already been downloaded for this revision of the group
      return;
    }
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(masterKey);
    GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(m.getServiceConfiguration()).forGroup(groupSecretParams);

    File tmpFile = Util.createTempFile();
    try (InputStream input = m.getMessageReceiver().retrieveGroupsV2ProfileAvatar(group.getAvatar(), tmpFile, ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
      byte[] encryptedData = Util.readFully(input);
      byte[] decryptedData = groupOperations.decryptAvatar(encryptedData);
      OutputStream outputStream = new FileOutputStream(avatarFile);
      outputStream.write(decryptedData);
      lastAvatarFetch = revision;
    } catch (NonSuccessfulResponseCodeException e) {
      lastAvatarFetch = revision;
    } finally {
      try {
        Files.delete(tmpFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete received group avatar temp file " + tmpFile + ", ignoring: " + e.getMessage());
      }
    }
  }

  @JsonIgnore
  public byte[] getGroupID() {
    return GroupsUtil.GetIdentifierFromMasterKey(masterKey).serialize();
  }

  public int getLastAvatarFetch() { return lastAvatarFetch; }

  public static class GroupDeserializer extends JsonDeserializer<Group> {
    @Override
    public Group deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      try {
        GroupMasterKey masterKey = new GroupMasterKey(Base64.decode(node.get("masterKey").textValue()));
        int revision = 0;
        if (node.has("revision")) {
          revision = node.get("revision").asInt();
        }
        int lastAvatarFetch = 0;
        if (node.has("lastAvatarFetch")) {
          lastAvatarFetch = node.get("lastAvatarFetch").asInt();
        }
        DecryptedGroup group;
        if (node.has("group")) {
          group = DecryptedGroup.parseFrom(Base64.decode(node.get("group").textValue()));
        } else {
          DecryptedGroup.Builder builder = DecryptedGroup.newBuilder();

          if (node.has("title")) {
            builder.setTitle(node.get("title").textValue());
          }

          if (node.has("timer")) {
            int duration = node.get("timer").asInt();
            DecryptedTimer timer = DecryptedTimer.newBuilder().setDuration(duration).build();
            builder.setDisappearingMessagesTimer(timer);
          }

          if (node.has("members")) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext();) {
              JsonNode m = it.next();
              if (m.has("uuid")) {
                ByteString uuid = UuidUtil.toByteString(UUID.fromString(node.get("uuid").textValue()));
                DecryptedMember decryptedMember = DecryptedMember.newBuilder().setUuid(uuid).build();
                builder.addMembers(decryptedMember);
              }
            }
          }

          if (node.has("pendingMembers")) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext();) {
              JsonNode m = it.next();
              if (m.has("uuid")) {
                ByteString uuid = UuidUtil.toByteString(UUID.fromString(node.get("uuid").textValue()));
                DecryptedPendingMember decryptedMember = DecryptedPendingMember.newBuilder().setUuid(uuid).build();
                builder.addPendingMembers(decryptedMember);
              }
            }
          }

          if (node.has("requestingMembers")) {
            for (Iterator<JsonNode> it = node.elements(); it.hasNext();) {
              JsonNode m = it.next();
              if (m.has("uuid")) {
                ByteString uuid = UuidUtil.toByteString(UUID.fromString(node.get("uuid").textValue()));
                DecryptedRequestingMember decryptedMember = DecryptedRequestingMember.newBuilder().setUuid(uuid).build();
                builder.addRequestingMembers(decryptedMember);
              }
            }
          }

          group = builder.build();
        }
        DistributionId distributionId = null;
        if (node.has("distributionId")) {
          distributionId = DistributionId.from(node.get("distributionId").asText());
        }
        return new Group(masterKey, revision, group, distributionId, lastAvatarFetch);
      } catch (InvalidInputException e) {
        logger.error("error deserializing group from legacy storage", e);
        throw new IOException(e.getMessage());
      }
    }
  }

  public static class GroupSerializer extends JsonSerializer<Group> {
    @Override
    public void serialize(Group value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      ObjectNode node = JsonNodeFactory.instance.objectNode();
      node.put("masterKey", Base64.encodeBytes(value.masterKey.serialize()));
      node.put("revision", value.revision);
      if (value.group != null) {
        node.put("group", Base64.encodeBytes(value.group.toByteArray()));
      }
      node.put("lastAvatarFetch", value.lastAvatarFetch);
      if (value.hasDistributionId()) {
        node.put("distributionId", value.getDistributionId().toString());
      }
      gen.writeObject(node);
    }
  }
}
