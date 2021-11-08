/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Account;
import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@ProtocolType("typing")
@Doc("send a typing started or stopped message")
public class TypingRequest implements RequestType<Empty> {
  private static Logger logger = LogManager.getLogger();

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("true") @Required public boolean typing;
  public long when;

  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRecipientError, InvalidGroupError,
                                           UntrustedIdentityError, UnknownGroupError, InvalidRequestError {
    Manager m = Common.getManager(account);
    Account a = Common.getAccount(account);

    byte[] groupId = null;
    if (group != null) {
      try {
        groupId = Base64.decode(group);
      } catch (IOException e) {
        throw new InvalidGroupError();
      }
    }
    if (groupId == null) {
      groupId = new byte[0];
    }

    if (when == 0) {
      when = System.currentTimeMillis();
    }

    SignalServiceTypingMessage.Action action = typing ? SignalServiceTypingMessage.Action.STARTED : SignalServiceTypingMessage.Action.STOPPED;
    SignalServiceTypingMessage message = new SignalServiceTypingMessage(action, when, Optional.fromNullable(groupId));
    SignalServiceMessageSender messageSender = m.getMessageSender();

    if (address != null) {
      Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);
      try {
        messageSender.sendTyping(recipient.getAddress(), m.getAccessPairFor(recipient), message);
      } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
        try {
          Common.getAccount(account).getProtocolStore().saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
        } catch (IOException | SQLException exception) {
          logger.error("internal error while saving new identity", exception);
        }
        throw new UntrustedIdentityError(m.getACI(), e);
      } catch (IOException e) {
        throw new InternalError("error sending typing message", e);
      }
    } else {
      GroupsTable.Group g = Common.getGroup(a.getGroupsTable(), group);
      List<Recipient> recipients;
      try {
        recipients = g.getMembers();
      } catch (IOException | SQLException e) {
        throw new InternalError("error getting group members", e);
      }

      List<SignalServiceAddress> recipientAddresses = recipients.stream().map(Recipient::getAddress).collect(Collectors.toList());
      try {
        messageSender.sendTyping(recipientAddresses, m.getAccessPairFor(recipients), message, null);
      } catch (IOException e) {
        throw new InternalError("error sending typing message", e);
      }
    }
    return new Empty();
  }
}
