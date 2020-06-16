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

import org.whispersystems.signalservice.api.messages.multidevice.ContactsMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.StickerPackOperationMessage;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

class JsonSyncMessage {
    JsonSentTranscriptMessage sent;
    JsonAttachment contacts;
    boolean contactsComplete;
    JsonAttachment groups;
    List<String> blockedNumbers;
    List<String> blockedGroups;
    String requestType;
    List<ReadMessage> readMessages;
    JsonViewOnceOpenMessage viewOnceOpen;
    JsonVerifiedMessage verified;
    JsonConfigurationMessage configuration;
    List<JsonStickerPackOperationMessage> stickerPackOperations = new LinkedList<>();

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
            blockedNumbers = syncMessage.getBlockedList().get().getNumbers();
            for(byte[] groupId : syncMessage.getBlockedList().get().getGroupIds()) {
                blockedGroups.add(Base64.encodeBytes(groupId));
            }
        }

        if(syncMessage.getRequest().isPresent()) {
            requestType = syncMessage.getRequest().get().getRequest().toString();
        }

        if(syncMessage.getRead().isPresent()) {
            this.readMessages = syncMessage.getRead().get();
        }

        if(syncMessage.getViewOnceOpen().isPresent()) {
            this.viewOnceOpen = new JsonViewOnceOpenMessage(syncMessage.getViewOnceOpen().get());
        }

        if(syncMessage.getVerified().isPresent()) {
           this.verified = new JsonVerifiedMessage(syncMessage.getVerified().get());
        }

        if(syncMessage.getStickerPackOperations().isPresent()) {
          for(StickerPackOperationMessage message : syncMessage.getStickerPackOperations().get()) {
            stickerPackOperations.add(new JsonStickerPackOperationMessage(message));
          }
        }
    }
}
