/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import static io.finn.signald.annotations.ExactlyOneOfRequired.RECIPIENT;

import io.finn.signald.Account;
import io.finn.signald.Empty;
import io.finn.signald.annotations.*;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.UnidentifiedAccessUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceTypingMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.util.Base64;

@ProtocolType("typing")
@Doc("send a typing started or stopped message")
public class TypingRequest implements RequestType<Empty> {

  @ExampleValue(ExampleValue.LOCAL_UUID) @Doc("The account to use") @Required public String account;
  @ExactlyOneOfRequired(RECIPIENT) public JsonAddress address;
  @ExampleValue(ExampleValue.GROUP_ID) @ExactlyOneOfRequired(RECIPIENT) public String group;
  @ExampleValue("true") @Required public boolean typing;
  public long when;

  @Override
  public Empty run(Request request) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError, InvalidRecipientError, InvalidGroupError,
                                           UntrustedIdentityError, UnknownGroupError, InvalidRequestError, UnregisteredUserError, AuthorizationFailedError, SQLError {
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
    SignalServiceTypingMessage message = new SignalServiceTypingMessage(action, when, Optional.of(groupId));
    SignalServiceMessageSender messageSender;
    try {
      messageSender = a.getSignalDependencies().getMessageSender();
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (IOException e) {
      throw new InternalError("unexpected error getting message sender", e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    UnidentifiedAccessUtil unidentifiedAccessUtil = new UnidentifiedAccessUtil(a.getACI());

    if (address != null) {
      Recipient recipient = Common.getRecipient(a.getACI(), address);
      try {
        messageSender.sendTyping(List.of(recipient.getAddress()), List.of(unidentifiedAccessUtil.getAccessPairFor(recipient)), message, null);
      } catch (IOException e) {
        throw new InternalError("error sending typing message", e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (SQLException e) {
        throw new SQLError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
    } else {
      var g = Common.getGroup(a.getACI(), group);
      List<Recipient> recipients;
      try {
        recipients = g.getMembers();
      } catch (IOException | SQLException e) {
        throw new InternalError("error getting group members", e);
      }

      List<SignalServiceAddress> recipientAddresses = recipients.stream().map(Recipient::getAddress).collect(Collectors.toList());
      try {
        messageSender.sendTyping(recipientAddresses, unidentifiedAccessUtil.getAccessPairFor(recipients), message, null);
      } catch (IOException e) {
        throw new InternalError("error sending typing message", e);
      } catch (NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (SQLException e) {
        throw new SQLError(e);
      } catch (ServerNotFoundException e) {
        throw new ServerNotFoundError(e);
      } catch (InvalidProxyException e) {
        throw new InvalidProxyError(e);
      }
    }
    return new Empty();
  }
}
