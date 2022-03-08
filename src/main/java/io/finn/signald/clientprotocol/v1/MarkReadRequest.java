/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.Empty;
import io.finn.signald.Manager;
import io.finn.signald.SignalDependencies;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

@ProtocolType("mark_read")
public class MarkReadRequest implements RequestType<Empty> {

  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to interact with") @Required public String account;

  @Doc("The address that sent the message being marked as read") @Required public JsonAddress to;

  @ExampleValue(ExampleValue.MESSAGE_ID) @Doc("List of messages to mark as read") @Required public List<Long> timestamps;

  public Long when;

  @Override
  public Empty run(Request request)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, UntrustedIdentityError, UnregisteredUserError, AuthorizationFailedError {
    if (when == null) {
      when = System.currentTimeMillis();
    }
    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, timestamps, when);
    Manager m = Common.getManager(account);
    Recipient recipient = Common.getRecipient(Database.Get(m.getACI()).RecipientsTable, to);
    SignalServiceMessageSender sender = m.getMessageSender();

    SignalDependencies dependencies;
    try {
      dependencies = SignalDependencies.get(m.getACI());
    } catch (SQLException | IOException e) {
      throw new InternalError("error getting account", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      sender.sendReceipt(recipient.getAddress(), m.getAccessPairFor(recipient), message);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (IOException e) {
      throw new InternalError("error sending receipt", e);
    } catch (UntrustedIdentityException e) {
      throw new UntrustedIdentityError(m.getACI(), e);
    }

    List<ReadMessage> readMessages = new LinkedList<>();
    for (Long ts : timestamps) {
      readMessages.add(new ReadMessage(recipient.getAddress(), ts));
    }

    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      sender.sendSyncMessage(SignalServiceSyncMessage.forRead(readMessages), m.getAccessPairFor(m.getOwnRecipient()));
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (IOException e) {
      throw new InternalError("error sending sync message", e);
    } catch (UntrustedIdentityException e) {
      throw new UntrustedIdentityError(m.getACI(), e);
    }
    return new Empty();
  }
}
