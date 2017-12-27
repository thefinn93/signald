/**
 * Copyright (C) 2018 Finn Herzfeld
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.storage.contacts.ContactInfo;


class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
    final Manager m;

    private SocketManager sockets;

    public MessageReceiver(Manager m, SocketManager sockets) throws NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException, IOException {
        this.m = m;
        this.sockets = sockets;
    }

    public void run() {
      try {
        Boolean exitNow = false;
        while(!exitNow) {
          double timeout = 3600;
          boolean returnOnTimeout = true;
          if (timeout < 0) {
            returnOnTimeout = false;
            timeout = 3600;
          }
          boolean ignoreAttachments = false;
          try {
            this.m.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, this);
          } catch (IOException e) {
            System.out.println("IO Exception while receiving messages: " + e.getMessage());
          } catch (AssertionError e) {
            System.out.println("AssertionError occured while receiving messages: " + e.getMessage());
          }
        }
      } catch(Exception e) {
        e.printStackTrace();
      }
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
        try {
          SignalServiceAddress source = envelope.getSourceAddress();
          ContactInfo sourceContact = this.m.getContact(source.getNumber());
          if(content != null && content.getDataMessage().isPresent()) {
            JsonMessageEnvelope message = new JsonMessageEnvelope(envelope, content, this.m);
            this.sockets.broadcast(new MessageWrapper("message", message));
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
    }
}
