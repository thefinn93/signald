/*
 * Copyright (C) 2020 Finn Herzfeld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonBlockedListMessage;
import io.finn.signald.clientprotocol.v1.JsonMessageRequestResponseMessage;
import io.finn.signald.clientprotocol.v1.JsonReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class JsonSyncMessage {
    JsonSentTranscriptMessage sent;
    JsonAttachment contacts;
    boolean contactsComplete;
    JsonAttachment groups;
    JsonBlockedListMessage blockedList;
    String request;
    List<JsonReadMessage> readMessages;
    JsonViewOnceOpenMessage viewOnceOpen;
    JsonVerifiedMessage verified;
    ConfigurationMessage configuration;
    List<JsonStickerPackOperationMessage> stickerPackOperations;
    String fetchType;
    JsonMessageRequestResponseMessage messageRequestResponse;

    JsonSyncMessage(SignalServiceSyncMessage syncMessage, String username) throws IOException, NoSuchAccountException {
        if(syncMessage.getSent().isPresent()) {
            this.sent = new JsonSentTranscriptMessage(syncMessage.getSent().get(), username);
        }

        if(syncMessage.getContacts().isPresent()) {
          ContactsMessage c = syncMessage.getContacts().get();
          contacts = new JsonAttachment(c.getContactsStream(), username);
          contactsComplete = c.isComplete();
        }

        if(syncMessage.getGroups().isPresent()) {
          groups = new JsonAttachment(syncMessage.getGroups().get(), username);
        }

        if(syncMessage.getBlockedList().isPresent()) {
            blockedList = new JsonBlockedListMessage(syncMessage.getBlockedList().get());
        }

        if(syncMessage.getRequest().isPresent()) {
            request = syncMessage.getRequest().get().getRequest().getType().name();
        }

        if(syncMessage.getRead().isPresent()) {
            readMessages = new ArrayList<>();
            for(ReadMessage r : syncMessage.getRead().get()) {
             readMessages.add(new JsonReadMessage(r));
            }
        }

        if(syncMessage.getViewOnceOpen().isPresent()) {
            viewOnceOpen = new JsonViewOnceOpenMessage(syncMessage.getViewOnceOpen().get());
        }

        if(syncMessage.getVerified().isPresent()) {
           verified = new JsonVerifiedMessage(syncMessage.getVerified().get());
        }

        if(syncMessage.getConfiguration().isPresent()) {
            configuration = syncMessage.getConfiguration().get();
        }

        if(syncMessage.getStickerPackOperations().isPresent()) {
            stickerPackOperations = new ArrayList<>();
          for(StickerPackOperationMessage message : syncMessage.getStickerPackOperations().get()) {
            stickerPackOperations.add(new JsonStickerPackOperationMessage(message));
          }
        }

        if(syncMessage.getFetchType().isPresent()) {
            fetchType = syncMessage.getFetchType().get().name();
        }

        if(syncMessage.getMessageRequestResponse().isPresent()) {
            messageRequestResponse = new JsonMessageRequestResponseMessage(syncMessage.getMessageRequestResponse().get());
        }
    }
}
