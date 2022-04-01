package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ErrorDoc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.AuthorizationFailedError;
import io.finn.signald.clientprotocol.v1.exceptions.GroupPatchNotAcceptedError;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.UuidUtil;

@ProtocolType("unban_user")
@Doc("Unbans users from a group.")
@ErrorDoc(error = AuthorizationFailedError.class, doc = AuthorizationFailedError.DEFAULT_ERROR_DOC)
@ErrorDoc(error = GroupPatchNotAcceptedError.class, doc = GroupPatchNotAcceptedError.DEFAULT_ERROR_DOC)
public class UnbanUserRequest implements RequestType<JsonGroupV2Info> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @ExampleValue(ExampleValue.GROUP_ID) @JsonProperty("group_id") @Required public String groupId;

  @Required @Doc("List of users to unban") @JsonProperty("users") public List<JsonAddress> usersToUnban;

  @Override
  public JsonGroupV2Info run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, UnknownGroupError, GroupVerificationError, InternalError,
                                                     InvalidRequestError, AuthorizationFailedError, SQLError, GroupPatchNotAcceptedError {
    final Account a = Common.getAccount(account);
    final var group = Common.getGroup(a, groupId);
    var recipientsTable = Database.Get(a.getACI()).RecipientsTable;

    final Set<UUID> membersToUnban = new HashSet<>(this.usersToUnban.size());
    for (JsonAddress member : usersToUnban) {
      try {
        membersToUnban.add(recipientsTable.get(member).getUUID());
      } catch (UnregisteredUserException e) {
        logger.warn("Unregistered user");
        // allow unbanning users if they end up unregistered, because they can just register again
        if (member.getUUID() == null) {
          throw new InvalidRequestError("One of the input users is unregistered and we don't know their service identifier / UUID");
        }
        membersToUnban.add(member.getUUID());
      } catch (SQLException | IOException e) {
        throw new InternalError("error looking up member", e);
      }
    }

    final var groupOperations = Common.getGroupOperations(a, group);
    // have to simultaneously ban and remove users that are in the group
    final var change = groupOperations.createUnbanUuidsChange(membersToUnban);
    change.setSourceUuid(UuidUtil.toByteString(a.getUUID()));

    Common.updateGroup(a, group, change);

    return group.getJsonGroupV2Info();
  }
}
