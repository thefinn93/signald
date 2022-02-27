/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.clientprotocol.v1.JsonGroupJoinInfo;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.GroupCredentialsTable;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.jobs.RefreshProfileJob;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.storage.ProfileCredentialStore;
import io.finn.signald.util.GroupProtoUtil;
import io.finn.signald.util.GroupsUtil;
import io.reactivex.rxjava3.annotations.NonNull;
import io.sentry.Sentry;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupInviteLink;
import org.signal.storageservice.protos.groups.Member;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedGroupJoinInfo;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.auth.AuthCredentialResponse;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.groupsv2.*;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceGroupV2;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;
import org.whispersystems.util.Base64UrlSafe;

public class Groups {
  private final static Logger logger = LogManager.getLogger();

  private final GroupsV2Api groupsV2Api;
  private final GroupCredentialsTable credentials;
  private final GroupsTable groupsTable;
  private final ACI aci;
  private final GroupsV2Operations groupsV2Operations;
  private final SignalServiceConfiguration serviceConfiguration;

  public Groups(ACI aci) throws SQLException, ServerNotFoundException, IOException, InvalidProxyException, NoSuchAccountException {
    this.aci = aci;
    groupsV2Api = SignalDependencies.get(aci).getAccountManager().getGroupsV2Api();
    groupsTable = new GroupsTable(aci);
    credentials = new GroupCredentialsTable(aci);
    serviceConfiguration = AccountsTable.getServer(aci).getSignalServiceConfiguration();
    groupsV2Operations = GroupsUtil.GetGroupsV2Operations(serviceConfiguration);
  }

  public Optional<GroupsTable.Group> getGroup(SignalServiceGroupV2 incomingGroupV2Context)
      throws IOException, InvalidInputException, SQLException, VerificationFailedException, InvalidGroupStateException {
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(incomingGroupV2Context.getMasterKey());
    final byte[] signedGroupChange = incomingGroupV2Context.hasSignedGroupChange() ? incomingGroupV2Context.getSignedGroupChange() : null;
    return getGroup(groupSecretParams, incomingGroupV2Context.getRevision(), signedGroupChange);
  }

  public Optional<GroupsTable.Group> getGroup(GroupMasterKey masterKey, int revision)
      throws IOException, InvalidInputException, SQLException, VerificationFailedException, InvalidGroupStateException {
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(masterKey);
    return getGroup(groupSecretParams, revision, null);
  }

  public Optional<GroupsTable.Group> getGroup(GroupSecretParams groupSecretParams, int revision)
      throws IOException, InvalidInputException, SQLException, VerificationFailedException, InvalidGroupStateException {
    return getGroup(groupSecretParams, revision, null);
  }

  private Optional<GroupsTable.Group> getGroup(GroupSecretParams groupSecretParams, int revision, byte[] signedGroupChangeBytes)
      throws IOException, InvalidInputException, SQLException, VerificationFailedException, InvalidGroupStateException {
    final Optional<GroupsTable.Group> localGroup = groupsTable.get(groupSecretParams.getPublicParams().getGroupIdentifier());

    if (!localGroup.isPresent() || localGroup.get().getRevision() < revision || revision < 0) {
      int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
      AuthCredentialResponse authCredential = credentials.getCredential(groupsV2Api, today);
      GroupsV2AuthorizationString authorization = groupsV2Api.getGroupsV2AuthorizationString(aci, today, groupSecretParams, authCredential);

      Optional<GroupsTable.Group> latestServerGroup;
      try {
        DecryptedGroup decryptedGroup = groupsV2Api.getGroup(groupSecretParams, authorization);
        groupsTable.upsert(groupSecretParams.getMasterKey(), decryptedGroup);
        latestServerGroup = groupsTable.get(groupSecretParams.getPublicParams().getGroupIdentifier());
      } catch (NotInGroupException e) {
        if (localGroup.isPresent()) {
          localGroup.get().delete();
        }
        latestServerGroup = Optional.absent();
      }

      if (latestServerGroup.isPresent()) {
        maybePersistNewProfileKeys(localGroup, latestServerGroup.get(), signedGroupChangeBytes);
      }
      return latestServerGroup;
    } else {
      return localGroup;
    }
  }

  /**
   * Variant of {@link Groups#maybePersistNewProfileKeysOrThrow(Optional, GroupsTable.Group, byte[])} where all
   * typed exceptions are caught. Fills in missing profile keys from the mostRecentGroup, and uses either the
   * signedGroupChangeBytes or paging from the server with the localState as the starting revision to do authoritative
   * profile key updates.
   *
   * @param localState The local group state if present to use for paging if signedGroupChangeBytes is not applicable.
   * @param mostRecentGroup The most recent group state from the server. This is already stored in the signald database.
   * @param signedGroupChangeBytes Incoming bytes for a group changed signed by the GV2 server / storage service.
   */
  private void maybePersistNewProfileKeys(Optional<GroupsTable.Group> localState, @NonNull GroupsTable.Group mostRecentGroup, @Nullable byte[] signedGroupChangeBytes) {
    try {
      maybePersistNewProfileKeysOrThrow(localState, mostRecentGroup, signedGroupChangeBytes);
    } catch (InvalidGroupStateException | SQLException | ServerNotFoundException | NoSuchAccountException | InvalidProxyException | IOException | InvalidInputException |
             VerificationFailedException | InvalidKeyException e) {
      logger.error("failed to update profile keys from group");
      Sentry.captureException(e);
    }
  }

  private void maybePersistNewProfileKeysOrThrow(Optional<GroupsTable.Group> localState, @NonNull GroupsTable.Group mostRecentGroup, @Nullable byte[] signedGroupChangeBytes)
      throws InvalidGroupStateException, SQLException, ServerNotFoundException, NoSuchAccountException, InvalidProxyException, IOException, InvalidInputException,
             VerificationFailedException, InvalidKeyException {
    final GroupSecretParams groupSecretParams = mostRecentGroup.getSecretParams();
    Optional<DecryptedGroupChange> signedGroupChange;
    if (signedGroupChangeBytes != null) {
      try {
        signedGroupChange = decryptChange(mostRecentGroup, GroupChange.parseFrom(signedGroupChangeBytes), true);
      } catch (VerificationFailedException e) {
        logger.error("failed to verify incoming P2P group change");
        Sentry.captureException(e);
        signedGroupChange = Optional.absent();
      } catch (InvalidProtocolBufferException e) {
        logger.error("failed to parse incoming P2P group change");
        Sentry.captureException(e);
        signedGroupChange = Optional.absent();
      }
    } else {
      signedGroupChange = Optional.absent();
    }

    if (signedGroupChange.isPresent() && localState.isPresent() && localState.get().getRevision() + 1 == signedGroupChange.get().getRevision() &&
        mostRecentGroup.getRevision() == signedGroupChange.get().getRevision()) {
      // unlike the Android app, we've already updated to the latest server revision, so we don't have to apply anything
      logger.info("Getting profile keys from P2P group change");
      final ProfileKeySet profileKeys = new ProfileKeySet();
      profileKeys.addKeysFromGroupChange(signedGroupChange.get());
      profileKeys.addKeysFromGroupState(mostRecentGroup.getDecryptedGroup());
      persistLearnedProfileKeys(profileKeys);
      return;
    }

    final DecryptedGroup localStateNullable = localState.isPresent() ? localState.get().getDecryptedGroup() : null;
    GlobalGroupState inputGroupState;
    if (!GroupProtoUtil.isMember(aci.uuid(), mostRecentGroup.getDecryptedGroup().getMembersList())) {
      logger.info("Not a member, use latest only");
      inputGroupState = new GlobalGroupState(localStateNullable, Collections.singletonList(new ServerGroupLogEntry(mostRecentGroup.getDecryptedGroup(), null)));
    } else {
      int revisionWeWereAdded = GroupProtoUtil.findRevisionWeWereAdded(mostRecentGroup.getDecryptedGroup(), aci.uuid());
      int logsNeededFrom = localState.isPresent() ? Math.max(localState.get().getRevision(), revisionWeWereAdded) : revisionWeWereAdded;
      boolean includeFirstState = !localState.isPresent();
      logger.info("Requesting from server currentRevision: " + (localStateNullable != null ? localStateNullable.getRevision() : "null") + " logsNeededFrom: " + logsNeededFrom +
                  " includeFirstState: " + includeFirstState);
      inputGroupState = getFullMemberHistoryPage(groupSecretParams, localState, logsNeededFrom, includeFirstState);
    }

    final ProfileKeySet profileKeys = new ProfileKeySet();
    boolean hasMore = true;
    while (hasMore) {
      for (ServerGroupLogEntry entry : inputGroupState.getServerHistory()) {
        if (entry.getGroup() != null) {
          profileKeys.addKeysFromGroupState(entry.getGroup());
        }
        if (entry.getChange() != null) {
          profileKeys.addKeysFromGroupChange(entry.getChange());
        }
      }

      hasMore = inputGroupState.hasMore();
      if (hasMore) {
        logger.info("Request next page from server revision: nextPageRevision: " + inputGroupState.getNextPageRevision());
        inputGroupState = getFullMemberHistoryPage(groupSecretParams, localState, inputGroupState.getNextPageRevision(), false);
      }
    }
    persistLearnedProfileKeys(profileKeys);
  }

  void persistLearnedProfileKeys(@NonNull ProfileKeySet profileKeys)
      throws NoSuchAccountException, SQLException, IOException, ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    final ProfileCredentialStore profileCredentialStore = Manager.get(aci).getAccountData().profileCredentialStore;
    final var updated = profileCredentialStore.persistProfileKeySet(profileKeys);

    if (!updated.isEmpty()) {
      logger.info("Learned " + updated.size() + " new profile keys, fetching profiles");
      for (ProfileAndCredentialEntry updatedEntry : updated) {
        // since the key was updated, the check in queueIfNeeded will check against a lastUpdateTimestamp of 0
        RefreshProfileJob.queueIfNeeded(Manager.get(aci), updatedEntry);
      }
    }
  }

  private GlobalGroupState getFullMemberHistoryPage(GroupSecretParams groupSecretParams, Optional<GroupsTable.Group> localState, int logsNeededFromRevision,
                                                    boolean includeFirstState)
      throws IOException, SQLException, InvalidGroupStateException, InvalidInputException, VerificationFailedException {
    final GroupHistoryPage groupHistoryPage = getGroupHistoryPage(groupSecretParams, logsNeededFromRevision, includeFirstState);
    final ArrayList<ServerGroupLogEntry> history = new ArrayList<>(groupHistoryPage.getResults().size());

    for (DecryptedGroupHistoryEntry entry : groupHistoryPage.getResults()) {
      DecryptedGroup group = entry.getGroup().orNull();
      DecryptedGroupChange change = entry.getChange().orNull();
      if (group != null || change != null) {
        history.add(new ServerGroupLogEntry(group, change));
      }
    }

    final DecryptedGroup localGroup = localState.isPresent() ? localState.get().getDecryptedGroup() : null;
    return new GlobalGroupState(localGroup, history, groupHistoryPage.getPagingData());
  }

  public JsonGroupJoinInfo getGroupJoinInfo(URI uri) throws IOException, InvalidInputException, VerificationFailedException, GroupLinkNotActiveException, SQLException {
    String encoding = uri.getFragment();
    if (encoding == null || encoding.length() == 0) {
      return null;
    }
    byte[] bytes = Base64UrlSafe.decodePaddingAgnostic(encoding);
    GroupInviteLink groupInviteLink = GroupInviteLink.parseFrom(bytes);
    GroupInviteLink.GroupInviteLinkContentsV1 groupInviteLinkContentsV1 = groupInviteLink.getV1Contents();
    GroupMasterKey groupMasterKey = new GroupMasterKey(groupInviteLinkContentsV1.getGroupMasterKey().toByteArray());
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(groupMasterKey);
    DecryptedGroupJoinInfo decryptedGroupJoinInfo = getGroupJoinInfo(groupSecretParams, groupInviteLinkContentsV1.getInviteLinkPassword().toByteArray());
    return new JsonGroupJoinInfo(decryptedGroupJoinInfo, groupMasterKey);
  }

  public DecryptedGroupJoinInfo getGroupJoinInfo(GroupSecretParams groupSecretParams, byte[] password)
      throws IOException, VerificationFailedException, GroupLinkNotActiveException, InvalidInputException, SQLException {
    return groupsV2Api.getGroupJoinInfo(groupSecretParams, Optional.of(password), getAuthorizationForToday(groupSecretParams));
  }

  public GroupsTable.Group createGroup(String title, File avatar, List<Recipient> members, Member.Role memberRole, int timer)
      throws IOException, VerificationFailedException, InvalidGroupStateException, InvalidInputException, SQLException, NoSuchAccountException, ServerNotFoundException,
             InvalidKeyException, InvalidProxyException {
    GroupSecretParams groupSecretParams = GroupSecretParams.generate();

    Optional<byte[]> avatarBytes = Optional.absent();
    if (avatar != null) {
      avatarBytes = Optional.of(Files.readAllBytes(avatar.toPath()));
    }

    ProfileCredentialStore profileCredentialStore = Manager.get(aci).getAccountData().profileCredentialStore;
    GroupCandidate groupCandidateSelf = new GroupCandidate(aci.uuid(), Optional.of(profileCredentialStore.getProfileKeyCredential(aci)));
    Set<GroupCandidate> candidates = members.stream()
                                         .map(x -> {
                                           ProfileKeyCredential profileCredential = profileCredentialStore.getProfileKeyCredential(x.getACI());
                                           return new GroupCandidate(x.getUUID(), Optional.fromNullable(profileCredential));
                                         })
                                         .collect(Collectors.toSet());

    GroupsV2Operations.NewGroup newGroup = groupsV2Operations.createNewGroup(groupSecretParams, title, avatarBytes, groupCandidateSelf, candidates, memberRole, timer);
    groupsV2Api.putNewGroup(newGroup, getAuthorizationForToday(groupSecretParams));

    return getGroup(groupSecretParams, -1).get();
  }

  private GroupsV2AuthorizationString getAuthorizationForToday(GroupSecretParams groupSecretParams)
      throws IOException, VerificationFailedException, InvalidInputException, SQLException {
    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = credentials.getCredential(groupsV2Api, today);
    return groupsV2Api.getGroupsV2AuthorizationString(aci, today, groupSecretParams, authCredential);
  }

  public Pair<SignalServiceDataMessage.Builder, GroupsTable.Group> updateGroup(GroupsTable.Group group, GroupChange.Actions.Builder change)
      throws SQLException, VerificationFailedException, InvalidInputException, IOException {
    change.setSourceUuid(aci.toByteString());
    Pair<DecryptedGroup, GroupChange> groupChangePair = commitChange(group, change);

    GroupMasterKey masterKey = group.getMasterKey();
    byte[] signedChange = groupChangePair.second().toByteArray();

    SignalServiceGroupV2.Builder groupBuilder = SignalServiceGroupV2.newBuilder(masterKey).withRevision(group.getRevision()).withSignedGroupChange(signedChange);
    SignalServiceDataMessage.Builder updateMessage = SignalServiceDataMessage.newBuilder().asGroupMessage(groupBuilder.build());
    return new Pair<>(updateMessage, group);
  }

  private Pair<DecryptedGroup, GroupChange> commitChange(GroupsTable.Group group, GroupChange.Actions.Builder change)
      throws IOException, VerificationFailedException, InvalidInputException, SQLException {
    final GroupSecretParams groupSecretParams = group.getSecretParams();
    final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(groupSecretParams);
    final DecryptedGroup previousGroupState = group.getDecryptedGroup();
    final int nextRevision = previousGroupState.getRevision() + 1;
    final GroupChange.Actions changeActions = change.setRevision(nextRevision).build();
    final DecryptedGroupChange decryptedChange;
    final DecryptedGroup decryptedGroupState;

    try {
      decryptedChange = groupOperations.decryptChange(changeActions, aci.uuid());
      decryptedGroupState = DecryptedGroupUtil.apply(previousGroupState, decryptedChange);
    } catch (VerificationFailedException | InvalidGroupStateException | NotAbleToApplyGroupV2ChangeException e) {
      throw new IOException(e);
    }

    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = credentials.getCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authString = groupsV2Api.getGroupsV2AuthorizationString(aci, today, groupSecretParams, authCredential);
    GroupChange signedGroupChange = groupsV2Api.patchGroup(changeActions, authString, Optional.absent());
    group.setDecryptedGroup(decryptedGroupState);
    return new Pair<>(decryptedGroupState, signedGroupChange);
  }

  public String uploadNewAvatar(GroupSecretParams groupSecretParams, byte[] avatarBytes) throws SQLException, VerificationFailedException, InvalidInputException, IOException {
    return groupsV2Api.uploadAvatar(avatarBytes, groupSecretParams, getAuthorizationForToday(groupSecretParams));
  }

  public GroupChange commitJoinToServer(GroupChange.Actions changeActions, GroupInviteLinkUrl url)
      throws SQLException, VerificationFailedException, InvalidInputException, IOException {
    GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(url.getGroupMasterKey());
    return commitJoinToServer(changeActions, groupSecretParams, url.getPassword().serialize());
  }

  public GroupChange commitJoinToServer(GroupChange.Actions changeActions, GroupSecretParams groupSecretParams, byte[] password)
      throws IOException, VerificationFailedException, InvalidInputException, SQLException {
    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredentialResponse = credentials.getCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authString = groupsV2Api.getGroupsV2AuthorizationString(aci, today, groupSecretParams, authCredentialResponse);
    return groupsV2Api.patchGroup(changeActions, authString, Optional.fromNullable(password));
  }

  public Optional<DecryptedGroupChange> decryptChange(GroupsTable.Group group, GroupChange groupChange, boolean verifySignature)
      throws InvalidGroupStateException, InvalidProtocolBufferException, VerificationFailedException {
    final GroupsV2Operations.GroupOperations groupOperations = groupsV2Operations.forGroup(group.getSecretParams());
    return groupOperations.decryptChange(groupChange, verifySignature);
  }

  public GroupHistoryPage getGroupHistoryPage(GroupSecretParams groupSecretParams, int fromRevision, boolean includeFirstState)
      throws InvalidGroupStateException, IOException, VerificationFailedException, InvalidInputException, SQLException {
    int today = (int)TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis());
    AuthCredentialResponse authCredential = credentials.getCredential(groupsV2Api, today);
    GroupsV2AuthorizationString authString = groupsV2Api.getGroupsV2AuthorizationString(aci, today, groupSecretParams, authCredential);

    return groupsV2Api.getGroupHistoryPage(groupSecretParams, fromRevision, authString, includeFirstState);
  }
}
