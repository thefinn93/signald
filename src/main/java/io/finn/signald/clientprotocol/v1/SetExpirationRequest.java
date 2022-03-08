/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.util.Base64;

@ProtocolType("set_expiration")
@Doc("Set the message expiration timer for a thread. Expiration must be specified in seconds, set to 0 to disable timer")
public class SetExpirationRequest implements RequestType<SendResponse> {
  private static final Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("604800") @Required public int expiration;

  @Override
  public SendResponse run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, UnknownGroupError, GroupVerificationError,
                                                  InvalidRequestError, AuthorizationFailedError, UnregisteredUserError, SQLError {
    List<SendMessageResult> results;

    if (group != null) {
      if (group.length() == 44) {
        Account a = Common.getAccount(account);
        var storedGroup = Common.getGroup(a, group);
        GroupsV2Operations.GroupOperations groupOperations = Common.getGroupOperations(a, storedGroup);
        results = Common.updateGroup(a, storedGroup, groupOperations.createModifyGroupTimerChange(expiration));
      } else {
        logger.warn("v1 group support is being removed https://gitlab.com/signald/signald/-/issues/224");
        byte[] groupId;
        try {
          groupId = Base64.decode(group);
        } catch (IOException e) {
          throw new UnknownGroupError();
        }
        try {
          results = Common.getManager(account).setExpiration(groupId, expiration);
        } catch (IOException e) {
          throw new InternalError("error setting expiration", e);
        } catch (GroupNotFoundException | NotAGroupMemberException e) {
          throw new UnknownGroupError();
        } catch (SQLException e) {
          throw new SQLError(e);
        }
      }
    } else {
      Manager m = Common.getManager(account);
      Recipient recipient = Common.getRecipient(m.getACI(), address);
      try {
        results = m.setExpiration(recipient, expiration);
      } catch (IOException | SQLException e) {
        throw new InternalError("error setting expiration", e);
      }
    }

    return new SendResponse(results, 0);
  }
}
