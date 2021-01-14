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
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.util.GroupsUtil;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.local.*;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonSerialize(using = Group.GroupSerializer.class)
@JsonDeserialize(using = Group.GroupDeserializer.class)
public class Group {
  public GroupMasterKey masterKey;
  public int revision;
  public DecryptedGroup group;

  public Group(GroupMasterKey m, int r, DecryptedGroup d) {
    masterKey = m;
    revision = r;
    group = d;
  }

  @JsonIgnore
  public List<SignalServiceAddress> getMembers() {
    if (group == null) {
      return null;
    }
    List<SignalServiceAddress> l = new ArrayList<>();
    for (DecryptedMember member : group.getMembersList()) {
      UUID uuid = DecryptedGroupUtil.toUuid(member);
      l.add(new SignalServiceAddress(Optional.of(uuid), Optional.absent()));
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
      UUID uuid = DecryptedGroupUtil.toUuid(member);
      l.add(new SignalServiceAddress(Optional.of(uuid), Optional.absent()));
    }
    return l;
  }

  public SignalServiceGroupV2 getSignalServiceGroupV2() {
    GroupMasterKey groupMasterKey = getMasterKey();
    return SignalServiceGroupV2.newBuilder(groupMasterKey).withRevision(revision).build();
  }

  public GroupMasterKey getMasterKey() { return masterKey; }

  public DecryptedGroup getGroup() { return group; }

  public boolean isPendingMember(UUID query) {
    for (UUID m : DecryptedGroupUtil.pendingToUuidList(group.getPendingMembersList())) {
      if (m.equals(query)) {
        return true;
      }
    }
    return false;
  }

  public List<SignalServiceAddress> getMembersIncludingPendingWithout(UUID self) {
    SignalServiceAddress address = new SignalServiceAddress(Optional.of(self), Optional.absent());
    return Stream.concat(getMembers().stream(), getPendingMembers().stream()).filter(member -> !member.matches(address)).collect(Collectors.toList());
  }

  public int getTimer() {
    if (group == null) {
      return 0;
    }
    return group.getDisappearingMessagesTimer().getDuration();
  }

  public JsonGroupV2Info getJsonGroupV2Info() { return new JsonGroupV2Info(SignalServiceGroupV2.newBuilder(masterKey).withRevision(revision).build(), group); }

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
        return new Group(masterKey, revision, group);
      } catch (InvalidInputException e) {
        e.printStackTrace();
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
      gen.writeObject(node);
    }
  }
}
