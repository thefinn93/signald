package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.Groups;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.IGroupsTable;
import java.io.IOException;
import java.sql.SQLException;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;

@ProtocolType("get_group_revision_pages")
@ErrorDoc(error = AuthorizationFailedError.class, doc = "caused when not a member of the group, when requesting logs from a revision lower than your joinedAtVersion, etc.")
@Doc("Query the server for group revision history. The history contains information about the changes between each revision and the user that made the change.")
public class GetGroupRevisionPagesRequest implements RequestType<GroupHistoryPage> {
  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to interact with") @Required public String account;

  @JsonProperty("group_id") @ExampleValue(ExampleValue.GROUP_ID) @Required public String groupId;

  @JsonProperty("from_revision")
  @Required
  @Doc("The revision to start the pages from. Note that if this is lower than the revision you joined the group, an AuthorizationFailedError is returned.")
  public int fromRevision;

  @JsonProperty("include_first_revision") @Doc("Whether to include the first state in the returned pages (default false)") public boolean includeFirstRevision = false;

  @Override
  public GroupHistoryPage run(Request request) throws NoSuchAccountError, UnknownGroupError, ServerNotFoundError, InvalidProxyError, InternalError, GroupVerificationError,
                                                      InvalidGroupStateError, InvalidRequestError, AuthorizationFailedError, RateLimitError, SQLError {
    final Account acc = Common.getAccount(account);
    // use revision == 0 to avoid unnecessarily fetching from the server
    final IGroupsTable.IGroup group = Common.getGroup(acc, groupId, 0);

    final Groups groups = Common.getGroups(acc);
    final org.whispersystems.signalservice.api.groupsv2.GroupHistoryPage page;
    try {
      page = groups.getGroupHistoryPage(group.getSecretParams(), fromRevision, includeFirstRevision);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (RateLimitException e) {
      throw new RateLimitError(e);
    } catch (VerificationFailedException e) {
      throw new GroupVerificationError(e);
    } catch (InvalidGroupStateException e) {
      throw new InvalidGroupStateError(e);
    } catch (IOException | SQLException | InvalidInputException e) {
      throw new InternalError("error getting group history page", e);
    }

    return new GroupHistoryPage(group, page);
  }
}
