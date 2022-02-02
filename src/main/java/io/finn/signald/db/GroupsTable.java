/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.db;

import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.Account;
import io.finn.signald.ServiceConfig;
import io.finn.signald.Util;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.GroupsUtil;
import java.io.*;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.util.Base64;

public class GroupsTable {
  private static String groupAvatarPath;

  private static final Logger logger = LogManager.getLogger();

  private static final String TABLE_NAME = "groups";
  private static final String ROWID = "rowid";
  private static final String ACCOUNT_UUID = "account_uuid";
  private static final String GROUP_ID = "group_id";
  private static final String MASTER_KEY = "master_key";
  private static final String REVISION = "revision";
  private static final String LAST_AVATAR_FETCH = "last_avatar_fetch";
  private static final String DISTRIBUTION_ID = "distribution_id";
  private static final String GROUP_INFO = "group_info";

  private final ACI aci;

  public GroupsTable(ACI aci) { this.aci = aci; }

  public Optional<Group> get(SignalServiceGroupV2 group) throws InvalidProtocolBufferException, InvalidInputException, SQLException {
    return get(GroupSecretParams.deriveFromMasterKey(group.getMasterKey()).getPublicParams().getGroupIdentifier());
  }
  public Optional<Group> get(GroupIdentifier identifier) throws SQLException, InvalidInputException, InvalidProtocolBufferException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + ROWID + ", * FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ? AND " + GROUP_ID + " = ?");
    statement.setString(1, aci.toString());
    statement.setBytes(2, identifier.serialize());
    ResultSet rows = Database.executeQuery(TABLE_NAME + "_get", statement);
    if (!rows.next()) {
      return Optional.absent();
    }
    return Optional.of(new Group(rows));
  }

  public List<Group> getAll() throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("SELECT " + ROWID + ",* FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, aci.toString());
    ResultSet rows = Database.executeQuery(TABLE_NAME + "_get_all", statement);
    List<Group> allGroups = new ArrayList<>();
    while (rows.next()) {
      try {
        allGroups.add(new Group(rows));
      } catch (InvalidInputException | InvalidProtocolBufferException e) {
        logger.error("error parsing group " + rows.getString(GROUP_ID) + " from database", e);
      }
    }
    return allGroups;
  }

  public void upsert(GroupMasterKey masterKey, int revision, DecryptedGroup decryptedGroup) throws SQLException { upsert(masterKey, revision, decryptedGroup, null, -1); }

  public void upsert(GroupMasterKey masterKey, int revision, DecryptedGroup decryptedGroup, DistributionId distributionId, int lastAvatarFetch) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement(
        "INSERT INTO " + TABLE_NAME + "(" + ACCOUNT_UUID + "," + GROUP_ID + "," + MASTER_KEY + "," + REVISION + "," + DISTRIBUTION_ID + "," + LAST_AVATAR_FETCH + "," + GROUP_INFO +
        ") VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (" + ACCOUNT_UUID + "," + GROUP_ID + ") DO UPDATE SET " + REVISION + "=excluded." + REVISION + "," + DISTRIBUTION_ID +
        "=excluded." + DISTRIBUTION_ID + "," + LAST_AVATAR_FETCH + "=excluded." + LAST_AVATAR_FETCH + "," + GROUP_INFO + "=excluded." + GROUP_INFO);
    int i = 1;
    statement.setString(i++, aci.toString());
    statement.setBytes(i++, GroupSecretParams.deriveFromMasterKey(masterKey).getPublicParams().getGroupIdentifier().serialize());
    statement.setBytes(i++, masterKey.serialize());
    statement.setInt(i++, revision);
    statement.setString(i++, distributionId == null ? null : distributionId.toString());
    statement.setInt(i++, lastAvatarFetch);
    statement.setBytes(i++, decryptedGroup.toByteArray());
    Database.executeUpdate(TABLE_NAME + "_upsert", statement);
  }

  private static File getGroupAvatarFile(GroupIdentifier groupId) { return new File(groupAvatarPath, "group-" + Base64.encodeBytes(groupId.serialize()).replace("/", "_")); }

  public static void deleteAccount(UUID uuid) throws SQLException {
    PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ACCOUNT_UUID + " = ?");
    statement.setString(1, uuid.toString());
    Database.executeUpdate(TABLE_NAME + "_delete_account", statement);
  }

  public static void setGroupAvatarPath(String path) throws IOException {
    groupAvatarPath = path;
    Files.createDirectories(new File(groupAvatarPath).toPath());
  }

  public static class Group {
    private final int rowId;
    private final Account account;
    private final GroupMasterKey masterKey;
    private int revision;
    private int lastAvatarFetch;
    private DistributionId distributionId;
    private DecryptedGroup group;

    private Group(ResultSet row) throws SQLException, InvalidInputException, InvalidProtocolBufferException {
      account = new Account(UUID.fromString(row.getString(ACCOUNT_UUID)));
      rowId = row.getInt(ROWID);
      masterKey = new GroupMasterKey(row.getBytes(MASTER_KEY));
      revision = row.getInt(REVISION);
      lastAvatarFetch = row.getInt(LAST_AVATAR_FETCH);
      String distributionIdString = row.getString(DISTRIBUTION_ID);
      distributionId = distributionIdString == null ? null : DistributionId.from(distributionIdString);
      group = DecryptedGroup.parseFrom(row.getBytes(GROUP_INFO));
    }

    public GroupIdentifier getId() { return GroupsUtil.GetIdentifierFromMasterKey(masterKey); }

    public String getIdString() { return Base64.encodeBytes(getId().serialize()); }

    public int getRevision() { return revision; }

    public GroupMasterKey getMasterKey() { return masterKey; }

    public GroupSecretParams getSecretParams() { return GroupSecretParams.deriveFromMasterKey(masterKey); }

    public DecryptedGroup getDecryptedGroup() { return group; }

    public void setDecryptedGroup(DecryptedGroup decryptedGroup) throws SQLException {
      PreparedStatement statement = Database.getConn().prepareStatement("UPDATE " + TABLE_NAME + " SET " + REVISION + " = ?, " + GROUP_INFO + " = ? WHERE " + ROWID + " = ?");
      statement.setInt(1, decryptedGroup.getRevision());
      statement.setBytes(2, decryptedGroup.toByteArray());
      statement.setInt(3, rowId);
      Database.executeUpdate(TABLE_NAME + "_set_decrypted_group", statement);
      revision = decryptedGroup.getRevision();
      this.group = decryptedGroup;
    }

    public SignalServiceGroupV2 getSignalServiceGroupV2() { return SignalServiceGroupV2.newBuilder(masterKey).withRevision(revision).build(); }

    public void delete() throws SQLException {
      PreparedStatement statement = Database.getConn().prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE " + ROWID + " = ?");
      statement.setInt(1, rowId);
      Database.executeUpdate(TABLE_NAME + "_delete", statement);
    }

    public JsonGroupV2Info getJsonGroupV2Info() {
      try {
        fetchAvatar();
      } catch (IOException | InvalidProxyException | SQLException | ServerNotFoundException | NoSuchAccountException e) {
        logger.warn("Failed to fetch group avatar: " + e.getMessage());
        logger.debug("stack trace for group avi fetch failure: ", e);
      }
      JsonGroupV2Info jsonGroupV2Info = new JsonGroupV2Info(SignalServiceGroupV2.newBuilder(masterKey).withRevision(revision).build(), group);
      File avatarFile = getGroupAvatarFile(getId());
      if (avatarFile.exists()) {
        jsonGroupV2Info.avatar = avatarFile.getAbsolutePath();
      }
      return jsonGroupV2Info;
    }

    private void fetchAvatar() throws IOException, InvalidProxyException, SQLException, ServerNotFoundException, NoSuchAccountException {
      File avatarFile = getGroupAvatarFile(getId());
      if (lastAvatarFetch == revision) {
        // group avatar has already been downloaded for this revision of the group
        return;
      }
      GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(masterKey);
      GroupsV2Operations.GroupOperations groupOperations = GroupsUtil.GetGroupsV2Operations(account.getServiceConfiguration()).forGroup(groupSecretParams);

      File tmpFile = Util.createTempFile();
      try (InputStream input =
               account.getSignalDependencies().getMessageReceiver().retrieveGroupsV2ProfileAvatar(group.getAvatar(), tmpFile, ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
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

    public List<Recipient> getMembers() throws IOException, SQLException {
      RecipientsTable recipientsTable = account.getRecipients();
      List<Recipient> recipients = new ArrayList<>();
      for (DecryptedMember member : group.getMembersList()) {
        Recipient recipient = recipientsTable.get(UuidUtil.fromByteString(member.getUuid()));
        recipients.add(recipient);
      }
      return recipients;
    }

    public List<Recipient> getPendingMembers() throws IOException, SQLException {
      RecipientsTable recipientsTable = account.getRecipients();
      List<Recipient> recipients = new ArrayList<>();
      for (DecryptedPendingMember member : group.getPendingMembersList()) {
        Recipient recipient = recipientsTable.get(UuidUtil.fromByteString(member.getUuid()));
        recipients.add(recipient);
      }
      return recipients;
    }

    public List<Recipient> getRequestingMembers() throws IOException, SQLException {
      RecipientsTable recipientsTable = account.getRecipients();
      List<Recipient> recipients = new ArrayList<>();
      for (DecryptedRequestingMember member : group.getRequestingMembersList()) {
        Recipient recipient = recipientsTable.get(UuidUtil.fromByteString(member.getUuid()));
        recipients.add(recipient);
      }
      return recipients;
    }

    public boolean isAdmin(Recipient recipient) {
      for (DecryptedMember member : group.getMembersList()) {
        if (UuidUtil.fromByteString(member.getUuid()).equals(recipient.getUUID())) {
          return member.getRole() == Member.Role.ADMINISTRATOR;
        }
      }
      return false;
    }

    public DistributionId getOrCreateDistributionId() throws SQLException {
      if (distributionId == null) {
        distributionId = DistributionId.create();
        PreparedStatement statement = Database.getConn().prepareStatement("UPDATE " + TABLE_NAME + " SET " + DISTRIBUTION_ID + " = ? WHERE " + ROWID + " = ?");
        statement.setString(1, distributionId.toString());
        statement.setInt(2, rowId);
        Database.executeUpdate(TABLE_NAME + "_create_distribution_id", statement);
      }
      return distributionId;
    }
  }
}
