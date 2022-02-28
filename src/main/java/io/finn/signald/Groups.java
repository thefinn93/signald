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
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.GetProfileKeysFromGroupHistoryJob;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
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
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

public class Groups {
  private final static Logger logger = LogManager.getLogger();
  /**
   * Corresponds to LOG_VERSION_LIMIT at
   * https://github.com/signalapp/storage-service/blob/master/src/main/java/org/signal/storageservice/controllers/GroupsController.java#L69
   */
  private final static int MAX_GROUP_CHANGES_PER_PAGE = 64;
  /**
   * From signald logs, it can take around 16s to get 45 pages of group history (~2.8125 pages per second), where each
   * page has at most 64 group changes each. Let's limit this to at most ~3 seconds to help with message processing
   * speeds.
   */
  private final static int MAX_NON_BACKGROUND_THREAD_GROUP_REVISIONS_FOR_PROFILE_KEYS = MAX_GROUP_CHANGES_PER_PAGE * 6;

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
   * typed exceptions are caught.Fills in missing profile keys from the mostRecentGroupState, and uses either the
   * signedGroupChangeBytes or paging from the server with the previousGroupState as the starting revision to do authoritative
   * profile key updates.
   *
   * Rationale for creating this catching version: If we inlined this above, these errors would just get propagated and
   * halt any other code from running (this can be run in a message receiver). Since all this is just opportunistic, we
   * shouldn't just hard fail everything else if we can't get profile keys.
   *
   * @param previousGroupState The local group state if present to use for paging if signedGroupChangeBytes is not applicable.
   * @param mostRecentGroupState The most recent group state from the server. This is already stored in the signald database.
   * @param signedGroupChangeBytes Incoming bytes for a group changed signed by the GV2 server / storage service.
   */
  private void maybePersistNewProfileKeys(Optional<GroupsTable.Group> previousGroupState, @NonNull GroupsTable.Group mostRecentGroupState,
                                          @Nullable byte[] signedGroupChangeBytes) {
    try {
      maybePersistNewProfileKeysOrThrow(previousGroupState, mostRecentGroupState, signedGroupChangeBytes);
    } catch (InvalidGroupStateException | SQLException | ServerNotFoundException | NoSuchAccountException | InvalidProxyException | IOException | InvalidInputException |
             VerificationFailedException | InvalidKeyException e) {
      logger.error("failed to update profile keys from group");
      Sentry.captureException(e);
    }
  }

  private void maybePersistNewProfileKeysOrThrow(Optional<GroupsTable.Group> previousGroupState, @NonNull GroupsTable.Group mostRecentGroupState,
                                                 @Nullable byte[] signedGroupChangeBytes) throws InvalidGroupStateException, SQLException, ServerNotFoundException,
                                                                                                 NoSuchAccountException, InvalidProxyException, IOException, InvalidInputException,
                                                                                                 VerificationFailedException, InvalidKeyException {
    final GroupSecretParams groupSecretParams = mostRecentGroupState.getSecretParams();
    final String groupId = Base64.encodeBytes(groupSecretParams.getPublicParams().getGroupIdentifier().serialize());

    if (previousGroupState.isPresent()) {
      if (previousGroupState.get().getRevision() >= mostRecentGroupState.getRevision()) {
        logger.debug("Group revision for " + groupId + " already up-to-date; skipping persisting of profile keys");
        return;
      }

      Optional<DecryptedGroupChange> signedGroupChange;
      if (signedGroupChangeBytes != null) {
        try {
          signedGroupChange = decryptChange(mostRecentGroupState, GroupChange.parseFrom(signedGroupChangeBytes), true);
        } catch (VerificationFailedException e) {
          logger.error("failed to verify incoming P2P group change for " + groupId);
          Sentry.captureException(e);
          signedGroupChange = Optional.absent();
        } catch (InvalidProtocolBufferException e) {
          logger.error("failed to parse incoming P2P group change for " + groupId);
          Sentry.captureException(e);
          signedGroupChange = Optional.absent();
        }
      } else {
        signedGroupChange = Optional.absent();
      }

      if (signedGroupChange.isPresent() && previousGroupState.get().getRevision() + 1 == signedGroupChange.get().getRevision() &&
          mostRecentGroupState.getRevision() == signedGroupChange.get().getRevision()) {
        // unlike the Android app, we've already updated to the latest server revision, so we don't have to apply anything
        logger.info("Getting profile keys from P2P group change for " + groupId);
        final ProfileKeySet profileKeys = new ProfileKeySet();
        profileKeys.addKeysFromGroupChange(signedGroupChange.get());
        // note: Android app ALWAYS adds keys from the entire new group state, but probably just a left over from trying
        // to reuse code and using group states to perform local updates. We don't need to do that, since P2P group
        // changes we only apply if we have previous state, and P2P changes are relatively small.
        persistLearnedGroupProfileKeys(profileKeys);
        return;
      }
    }

    final GroupHistoryPage firstGroupHistoryPage;
    if (!GroupProtoUtil.isMember(aci.uuid(), mostRecentGroupState.getDecryptedGroup().getMembersList())) {
      logger.info("Not a member, use latest only for " + groupId);
      firstGroupHistoryPage = new GroupHistoryPage(
          Collections.singletonList(new DecryptedGroupHistoryEntry(Optional.of(mostRecentGroupState.getDecryptedGroup()), Optional.absent())), GroupHistoryPage.PagingData.NONE);
    } else if (!previousGroupState.isPresent()) {
      // When we connect signald as a new linked device, we can have !previousGroupState.isPresent() with a large number of
      // group pages to go through for profile keys, especially if we have groups that we've joined from other devices
      // that have gotten many group updates before we started to link signald. However, if we read the group history
      // pages for some of these groups, we can run into cases where signald is stuck with an outdated profile key.
      //
      // Suppose we are in groups A and B, and consider the following ordered events. Suppose Alice has a profile key
      // with version "1" (profile key versions don't seem to be comparable numbers; this is just for demonstration
      // purposes).
      //   1. Alice joins groups A. This adds an authoritative profile key update/addition in the group logs of group A
      //      with her profile key version at "1".
      //   2. Alice leaves group A.
      //   3. Alice joins groups B. This adds an authoritative profile key update/addition in the group logs of group B
      //      with her profile key version at "1".
      //   4. Alice blocks someone. Her app rotates the profile key to version "2". She updates her profile key inside
      //      of group B to this new version, but not inside of group A, because she left it.
      // Suppose now we connect signald as a linked device, and suppose signald receives from the main device or the
      // storage service groups B and A in that order. When signald processes the profile keys from group B, it will
      // see her version "2" profile key as an authoritative profile key update, and signald stores that. But, when
      // signald goes next to process profile keys from group A, it will see her version "1" profile key as an
      // authoritative profile key update, so it will store that version instead, replacing version "2" in the
      // ProfileCredentialStore with version "1".
      //
      // In the end, signald is left with an outdated profile key for Alice. Since profile key versions are not
      // comparable (seems to be based on HMAC-SHA256 of profile key bytes || UID bytes in a "stateful hash object"
      // construction), signald doesn't really know that the profile key in group A is outdated. All we can really do is
      // ensure we're not just replacing the same profile key. And Alice is not just one user; if there are multiple
      // users for which this situation applies, there will be a large number of outdated profiles.
      //
      // An easy workaround is to just not take profile keys from group logs for groups we haven't seen before, thus
      // not paging group history from the server for these new groups. This is tolerable, because signald doesn't use
      // group changes to advance its local copy of the group state. Also, members for which we share multiple groups
      // will send their new profile key as group changes for the other groups anyway. (Of course, there are edge cases
      // for when a user only updates a certain number of their groups with a new profile key because the server started
      // rate limiting them, they ran into network errors, etc.)
      //
      // We can't do something like filter out profile keys from members that are not in the group in the latest state,
      // because that would mess with users that request to join groups.
      logger.info("New group " + groupId + "; use only latest state");
      firstGroupHistoryPage = new GroupHistoryPage(
          Collections.singletonList(new DecryptedGroupHistoryEntry(Optional.of(mostRecentGroupState.getDecryptedGroup()), Optional.absent())), GroupHistoryPage.PagingData.NONE);
    } else {
      int revisionWeWereAdded = GroupProtoUtil.findRevisionWeWereAdded(mostRecentGroupState.getDecryptedGroup(), aci.uuid());
      int logsNeededFrom = Math.max(previousGroupState.get().getRevision(), revisionWeWereAdded);

      if (mostRecentGroupState.getRevision() - logsNeededFrom > MAX_NON_BACKGROUND_THREAD_GROUP_REVISIONS_FOR_PROFILE_KEYS) {
        // If there are too many pages, do paging for keys in a background thread. This should rarely happen, but it
        // could happen if a signald instance is offline for too long and the group revision has progressed much further
        // than what we have stored. The upcoming paging code can run inside a message receiver and other
        // performance-critical areas; we don't want to eat up too much time paging for profile keys for groups.
        logger.info("Too many group history pages for " + groupId + " (limit: " + MAX_NON_BACKGROUND_THREAD_GROUP_REVISIONS_FOR_PROFILE_KEYS +
                    "); enqueuing job to persist profile keys from pages starting from revision " + logsNeededFrom + " (mostRecentGroupState revision " +
                    mostRecentGroupState.getRevision() + ")");
        BackgroundJobRunnerThread.queue(new GetProfileKeysFromGroupHistoryJob(aci, groupSecretParams, logsNeededFrom, mostRecentGroupState.getRevision()));
        return;
      }

      logger.info("Paging group " + groupId +
                  " for authoritative profile keys. previousGroupState revision: " + (previousGroupState.transform(GroupsTable.Group::getRevision).orNull()) +
                  ", most recent revision: " + mostRecentGroupState.getRevision() + ", logsNeededFrom: " + logsNeededFrom);
      firstGroupHistoryPage = getGroupHistoryPage(groupSecretParams, logsNeededFrom, false);
    }
    // only include all (non-authoritative) profile keys if
    // this is a new group we haven't seen before <=> !previousGroupState.isPresent()
    persistProfileKeysFromServerGroupHistory(groupSecretParams, firstGroupHistoryPage, !previousGroupState.isPresent() ? mostRecentGroupState : null);
  }

  /**
   * Uses the group logs from the GV2 server to get profile keys to persist, and then persists them in the profile
   * credential store.
   *
   * @param groupSecretParams Params for the group
   * @param firstPage First page to use; typically can get it by calling {@link #getGroupHistoryPage(GroupSecretParams, int, boolean)}
   * @param mostRecentGroup The most recent group to use to fill in missing profile keys, if available.
   */
  public void persistProfileKeysFromServerGroupHistory(@NonNull final GroupSecretParams groupSecretParams, @NonNull final GroupHistoryPage firstPage,
                                                       @Nullable GroupsTable.Group mostRecentGroup) throws InvalidGroupStateException, IOException, VerificationFailedException,
                                                                                                           InvalidInputException, SQLException, NoSuchAccountException,
                                                                                                           ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    GroupHistoryPage currentPage = firstPage;
    final ProfileKeySet profileKeys = new ProfileKeySet();
    while (currentPage.getPagingData().hasMorePages()) {
      for (DecryptedGroupHistoryEntry entry : currentPage.getResults()) {
        if (entry.getGroup().isPresent()) {
          profileKeys.addKeysFromGroupState(entry.getGroup().get());
        }
        if (entry.getChange().isPresent()) {
          profileKeys.addKeysFromGroupChange(entry.getChange().get());
        }
      }
      if (currentPage.getPagingData().hasMorePages()) {
        final int nextPageRevision = currentPage.getPagingData().getNextPageRevision();
        logger.info("Request next page from server revision: nextPageRevision: " + nextPageRevision);
        currentPage = getGroupHistoryPage(groupSecretParams, nextPageRevision, false);
      }
    }

    if (mostRecentGroup != null) {
      profileKeys.addKeysFromGroupState(mostRecentGroup.getDecryptedGroup());
    }

    persistLearnedGroupProfileKeys(profileKeys);
  }

  private void persistLearnedGroupProfileKeys(@NonNull ProfileKeySet profileKeys)
      throws NoSuchAccountException, SQLException, IOException, ServerNotFoundException, InvalidKeyException, InvalidProxyException {
    final ProfileCredentialStore profileCredentialStore = Manager.get(aci).getAccountData().profileCredentialStore;
    final var updated = profileCredentialStore.persistProfileKeySet(profileKeys);

    if (!updated.isEmpty()) {
      logger.info("Learned " + updated.size() + " new profile keys, fetching profiles");
      for (ProfileAndCredentialEntry updatedEntry : updated) {
        // Since the key was updated, the check in queueIfNeeded will check against a lastUpdateTimestamp of 0.
        // Note: Android app does this refresh synchronously. This will slow down message processing perf, so we don't
        // do it (plus we already use RefreshProfileJob for individual profile key updates anyway). However, not running
        // this job synchronously means profile keys can be out of date for a time.
        RefreshProfileJob.queueIfNeeded(Manager.get(aci), updatedEntry);
      }
    }
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
