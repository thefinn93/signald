package io.finn.signald;

import com.google.protobuf.ByteString;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import io.reactivex.rxjava3.annotations.NonNull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember;
import org.whispersystems.signalservice.api.util.UuidUtil;

/**
 * Collects profile keys from group states.
 * <p>
 * Separates out "authoritative" profile keys that came from a group update created by their owner.
 * <p>
 * Authoritative profile keys can be used to overwrite local profile keys.
 * Non-authoritative profile keys can be used to fill in missing knowledge.
 */
public final class ProfileKeySet {
  private final static Logger logger = LogManager.getLogger();

  private final Map<Recipient, ProfileKey> profileKeys = new LinkedHashMap<>();
  private final Map<Recipient, ProfileKey> authoritativeProfileKeys = new LinkedHashMap<>();
  private final IRecipientsTable recipientsTable;

  public ProfileKeySet(IRecipientsTable recipientsTable) { this.recipientsTable = recipientsTable; }

  /**
   * Add new profile keys from a group change.
   * <p>
   * If the change came from the member whose profile key is changing then it is regarded as
   * authoritative.
   */
  public void addKeysFromGroupChange(@NonNull DecryptedGroupChange change) throws SQLException, IOException {
    UUID editor = UuidUtil.fromByteStringOrNull(change.getEditor());

    for (DecryptedMember member : change.getNewMembersList()) {
      addMemberKey(member, editor);
    }

    for (DecryptedMember member : change.getPromotePendingMembersList()) {
      addMemberKey(member, editor);
    }

    for (DecryptedMember member : change.getModifiedProfileKeysList()) {
      addMemberKey(member, editor);
    }

    for (DecryptedRequestingMember member : change.getNewRequestingMembersList()) {
      addMemberKey(editor, member.getUuid(), member.getProfileKey());
    }
  }

  /**
   * Add new profile keys from the group state.
   * <p>
   * Profile keys found in group state are never authoritative as the change cannot be easily
   * attributed to a member and it's possible that the group is out of date. So profile keys
   * gathered from a group state can only be used to fill in gaps in knowledge.
   */
  public void addKeysFromGroupState(@NonNull DecryptedGroup group) throws SQLException, IOException {
    for (DecryptedMember member : group.getMembersList()) {
      addMemberKey(member, null);
    }
  }

  private void addMemberKey(@NonNull DecryptedMember member, @Nullable UUID changeSource) throws SQLException, IOException {
    addMemberKey(changeSource, member.getUuid(), member.getProfileKey());
  }

  private void addMemberKey(@Nullable UUID changeSource, @NonNull ByteString memberUuidBytes, @NonNull ByteString profileKeyBytes) throws SQLException, IOException {
    UUID memberUuid = UuidUtil.fromByteString(memberUuidBytes);

    if (UuidUtil.UNKNOWN_UUID.equals(memberUuid)) {
      logger.warn("Seen unknown member UUID");
      return;
    }

    ProfileKey profileKey;
    try {
      profileKey = new ProfileKey(profileKeyBytes.toByteArray());
    } catch (InvalidInputException e) {
      logger.warn("Bad profile key in group");
      return;
    }

    Recipient recipient = recipientsTable.get(memberUuid);
    if (memberUuid.equals(changeSource)) {
      authoritativeProfileKeys.put(recipient, profileKey);
      profileKeys.remove(recipient);
    } else {
      if (!authoritativeProfileKeys.containsKey(recipient)) {
        profileKeys.put(recipient, profileKey);
      }
    }
  }

  public Map<Recipient, ProfileKey> getProfileKeys() { return profileKeys; }

  public Map<Recipient, ProfileKey> getAuthoritativeProfileKeys() { return authoritativeProfileKeys; }

  public Recipient getSelf() throws SQLException, IOException { return recipientsTable.self(); }
}
