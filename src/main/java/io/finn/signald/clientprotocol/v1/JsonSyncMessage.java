/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.JsonStickerPackOperationMessage;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.push.ACI;

public class JsonSyncMessage {
  public JsonSentTranscriptMessage sent;
  public JsonAttachment contacts;
  public boolean contactsComplete;
  public JsonAttachment groups;
  public JsonBlockedListMessage blockedList;
  public String request;
  public List<JsonReadMessage> readMessages;
  public JsonViewOnceOpenMessage viewOnceOpen;
  public JsonVerifiedMessage verified;
  public ConfigurationMessage configuration;
  public List<JsonStickerPackOperationMessage> stickerPackOperations;
  public String fetchType;
  public JsonMessageRequestResponseMessage messageRequestResponse;

  public JsonSyncMessage(SignalServiceSyncMessage syncMessage, ACI aci) throws InternalError, NoSuchAccountError, ServerNotFoundError, InvalidProxyError {
    if (syncMessage.getSent().isPresent()) {
      this.sent = new JsonSentTranscriptMessage(syncMessage.getSent().get(), aci);
    }

    if (syncMessage.getContacts().isPresent()) {
      ContactsMessage c = syncMessage.getContacts().get();
      contacts = new JsonAttachment(c.getContactsStream(), aci);
      contactsComplete = c.isComplete();
    }

    if (syncMessage.getGroups().isPresent()) {
      groups = new JsonAttachment(syncMessage.getGroups().get(), aci);
    }

    if (syncMessage.getBlockedList().isPresent()) {
      blockedList = new JsonBlockedListMessage(syncMessage.getBlockedList().get());
    }

    if (syncMessage.getRequest().isPresent()) {
      request = syncMessage.getRequest().get().getRequest().getType().name();
    }

    if (syncMessage.getRead().isPresent()) {
      readMessages = new ArrayList<>();
      for (ReadMessage r : syncMessage.getRead().get()) {
        readMessages.add(new JsonReadMessage(r));
      }
    }

    if (syncMessage.getViewOnceOpen().isPresent()) {
      viewOnceOpen = new JsonViewOnceOpenMessage(syncMessage.getViewOnceOpen().get());
    }

    if (syncMessage.getVerified().isPresent()) {
      verified = new JsonVerifiedMessage(syncMessage.getVerified().get());
    }

    if (syncMessage.getConfiguration().isPresent()) {
      configuration = syncMessage.getConfiguration().get();
    }

    if (syncMessage.getStickerPackOperations().isPresent()) {
      stickerPackOperations = new ArrayList<>();
      for (StickerPackOperationMessage message : syncMessage.getStickerPackOperations().get()) {
        stickerPackOperations.add(new JsonStickerPackOperationMessage(message));
      }
    }

    if (syncMessage.getFetchType().isPresent()) {
      fetchType = syncMessage.getFetchType().get().name();
    }

    if (syncMessage.getMessageRequestResponse().isPresent()) {
      messageRequestResponse = new JsonMessageRequestResponseMessage(syncMessage.getMessageRequestResponse().get());
    }
  }
}
