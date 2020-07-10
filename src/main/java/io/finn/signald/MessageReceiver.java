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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
    final String username;
    private SocketManager sockets;
    private static final Logger logger = LogManager.getLogger();

    public MessageReceiver(String username) {
      this.username = username;
      this.sockets = new SocketManager();
    }

    public void subscribe(Socket s) {
      this.sockets.add(s);
    }

    public boolean unsubscribe(Socket s) {
      boolean removed = sockets.remove(s);
      if(removed && sockets.size() == 0) {
          logger.info("Last client for " + this.username + " unsubscribed, shutting down message pipe!");
          try {
              Manager.get(username).shutdownMessagePipe();
          } catch(IOException | NoSuchAccountException e) {
              logger.catching(e);
          }
      }
      return removed;
    }


    public void run() {
      try {
        Thread.currentThread().setName(Util.redact(username) + "-receiver");
        Manager manager = Manager.get(username);
        while(sockets.size() > 0) {
          double timeout = 3600;
          boolean returnOnTimeout = true;
          boolean ignoreAttachments = false;
          try {
            manager.receiveMessages((long) (timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, this);
          } catch (IOException e) {
              logger.debug("probably harmless IOException while receiving messages:", e);
          } catch (AssertionError e) {
              logger.catching(e);
          }
        }
      } catch(Exception e) {
        logger.catching(e);
      }
    }

    @Override
    public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
      String type = "message";
      if(exception != null) {
        logger.catching(exception);
        type = "unreadable_message";
      }

      try {
        if(envelope != null) {
          JsonMessageEnvelope message = new JsonMessageEnvelope(envelope, content, username);
          this.sockets.broadcast(new JsonMessageWrapper(type, message, exception));
        } else {
          this.sockets.broadcast(new JsonMessageWrapper(type, null, exception));
        }
      } catch (IOException | NoSuchAccountException e) {
        logger.catching(e);
      }
    }
}
