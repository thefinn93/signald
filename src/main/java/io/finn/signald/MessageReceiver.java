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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.storage.contacts.ContactInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
    final String username;
    private Manager m;
    private ConcurrentHashMap<String,Manager> managers;
    private SocketManager sockets;
    private static final Logger logger = LogManager.getLogger();

    public MessageReceiver(String username, SocketManager sockets, ConcurrentHashMap<String,Manager> managers) throws NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException, IOException {
      this.sockets = sockets;
      this.managers = managers;
      this.username = username;
    }

    public void run() {
      try {
        String settingsPath = System.getProperty("user.home") + "/.config/signal";
        this.m = new Manager(this.username, settingsPath);
        Thread.currentThread().setName(this.username + "-manager");
        logger.info("Created new manager for " + username);
        this.managers.put(username, m);
        if(m.userExists()) {
          this.m.init();
          while(true) {
            double timeout = 3600;
            boolean returnOnTimeout = true;
            if (timeout < 0) {
              returnOnTimeout = false;
              timeout = 3600;
            }
            boolean ignoreAttachments = false;
            try {
              this.m.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, this);
            } catch (IOException | AssertionError e) {
                logger.catching(e);
            }
          }
        }
      } catch (org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException e) {
        logger.warn("Authorization Failed for " + username);
      } catch(Exception e) {
        logger.catching(e);
      }
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
      try {
        SignalServiceAddress source = envelope.getSourceAddress();
        ContactInfo sourceContact = this.m.getContact(source.getNumber());
        if(envelope != null) {
          JsonMessageEnvelope message = new JsonMessageEnvelope(envelope, content, this.m);
          this.sockets.broadcast(new JsonMessageWrapper("message", message));
        }
      } catch (IOException e) {
        logger.catching(e);
      }
    }
}
