package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.ByteString;
import io.finn.signald.Account;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.AuthorizationFailedError;
import io.finn.signald.clientprotocol.v1.exceptions.GroupVerificationError;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidRequestError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.SQLError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.clientprotocol.v1.exceptions.UnknownGroupError;
import io.finn.signald.db.Database;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("ban_user")
@Doc("Bans users from a group. This works even if the users aren't in the group. If they are currently in the group, they will also be removed.")
public class BanUserRequest implements RequestType<JsonGroupV2Info> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @JsonProperty("group_id") @Required public String groupId;

  @Required @Doc("List of users to ban") @JsonProperty("users") public List<JsonAddress> usersToBan;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, GroupVerificationError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError, SQLError {
    final Account a = Common.getAccount(account);
    final var group = Common.getGroup(a, groupId);
    final var decryptedGroup = group.getDecryptedGroup();

    var recipientsTable = Database.Get(a.getACI()).RecipientsTable;
    final Set<UUID> allUsersToBan = new HashSet<>(this.usersToBan.size());
    for (JsonAddress user : this.usersToBan) {
      try {
        allUsersToBan.add(recipientsTable.get(user).getServiceId().uuid());
      } catch (UnregisteredUserException e) {
        logger.info("Unregistered user");
        // allow banning users if they end up unregistered, because they can just register again
        if (user.getUUID() == null) {
          throw new InvalidRequestError("One of the input users is unregistered and we don't know their service identifier / UUID!");
        }
        allUsersToBan.add(user.getUUID());
      } catch (SQLException | IOException e) {
        throw new InternalError("error looking up user", e);
      }
    }

    final var groupOperations = Common.getGroupOperations(a, group);

    // have to simultaneously ban and remove users that are in the group membership lists
    final var finalChange = GroupChange.Actions.newBuilder();
    for (UUID userToBan : allUsersToBan) {
      final ByteString userToBanUuidBytes = UuidUtil.toByteString(userToBan);

      final GroupChange.Actions.Builder thisChange;
      if (decryptedGroup.getMembersList().stream().anyMatch(member -> member.getUuid().equals(userToBanUuidBytes))) {
        // builder: addAllDeleteMembers + addAllAddBannedMembers
        thisChange = groupOperations.createRemoveMembersChange(Collections.singleton(userToBan), true);
      } else if (decryptedGroup.getRequestingMembersList().stream().anyMatch(requesting -> requesting.getUuid().equals(userToBanUuidBytes))) {
        // builder: addAllDeleteRequestingMembers + addAllAddBannedMembers
        thisChange = groupOperations.createRefuseGroupJoinRequest(Collections.singleton(userToBan), true);
      } else {
        final var pending = decryptedGroup.getPendingMembersList().stream().filter(pendingMember -> pendingMember.getUuid().equals(userToBanUuidBytes)).findFirst();
        if (pending.isPresent()) {
          // builder: addAllDeletePendingMembers + addAllAddBannedMembers
          try {
            // doesn't come with alsoBan parameter
            thisChange = groupOperations.createRemoveInvitationChange(Collections.singleton(new UuidCiphertext(pending.get().getUuidCipherText().toByteArray())))
                             .addAllAddBannedMembers(groupOperations.createBanUuidsChange(Collections.singleton(userToBan)).getAddBannedMembersList());
          } catch (InvalidInputException e) {
            throw new InternalError("failed to get UuidCiphertext", e);
          }
        } else {
          // builder: addAllAddBannedMembers
          // ban them even though they're not in the group
          thisChange = groupOperations.createBanUuidsChange(Collections.singleton(userToBan));
        }
      }

      // some of these lists might be empty from the branching above
      finalChange.addAllAddBannedMembers(thisChange.getAddBannedMembersList())
          .addAllDeleteMembers(thisChange.getDeleteMembersList())
          .addAllDeleteRequestingMembers(thisChange.getDeleteRequestingMembersList())
          .addAllDeletePendingMembers(thisChange.getDeletePendingMembersList());
    }

    finalChange.setSourceUuid(UuidUtil.toByteString(a.getUUID()));

    Common.updateGroup(a, group, finalChange);

    return group.getJsonGroupV2Info();
  }
}
