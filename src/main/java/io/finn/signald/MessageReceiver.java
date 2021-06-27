/*
 * Copyright (C) 2021 Finn Herzfeld
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

import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.exceptions.NoSuchAccountException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.metadata.SelfSendException;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
  final String username;
  private final Manager m;
  private int backoff = 0;
  private final SocketManager sockets;
  private static final Logger logger = LogManager.getLogger();
  private static final HashMap<String, MessageReceiver> receivers = new HashMap<>();

  public MessageReceiver(String username) throws SQLException, IOException, NoSuchAccountException {
    this.username = username;
    this.m = Manager.get(username);
    this.sockets = new SocketManager();
  }

  public static synchronized void subscribe(String username, MessageEncoder receiver) throws SQLException, IOException, NoSuchAccountException {
    if (!receivers.containsKey(username)) {
      MessageReceiver r = new MessageReceiver(username);
      new Thread(r).start();
      receivers.put(username, r);
    }
    receivers.get(username).sockets.add(receiver);
    logger.debug("message receiver for " + Util.redact(username) + " got new subscriber. subscriber count: " + receivers.get(username).sockets.size());
  }

  public static synchronized boolean unsubscribe(String username, Socket s) {
    if (!receivers.containsKey(username)) {
      return false;
    }

    boolean removed = receivers.get(username).remove(s);
    if (removed) {
      logger.debug("message receiver for " + Util.redact(username) + " lost a subscriber. subscriber count: " + receivers.get(username).sockets.size());
    }
    if (removed && receivers.get(username).sockets.size() == 0) {
      logger.info("Last client for " + Util.redact(username) + " unsubscribed, shutting down message pipe");
      try {
        Manager.get(username).shutdownMessagePipe();
      } catch (IOException | NoSuchAccountException | SQLException e) {
        logger.catching(e);
      }
      receivers.remove(username);
    }
    return removed;
  }

  private boolean remove(Socket socket) { return sockets.remove(socket); }

  public static void unsubscribeAll(Socket s) {
    for (String r : receivers.keySet()) {
      unsubscribe(r, s);
    }
  }

  public void run() {
    boolean notifyOnConnect = true;
    logger.debug("starting message receiver for " + Util.redact(username));
    try {
      Thread.currentThread().setName(Util.redact(username) + "-receiver");
      while (sockets.size() > 0) {
        double timeout = 3600;
        boolean returnOnTimeout = true;
        boolean ignoreAttachments = false;
        try {
          if (notifyOnConnect) {
            this.sockets.broadcastListenStarted();
          } else {
            notifyOnConnect = true;
          }
          logger.debug("connecting to socket");
          m.receiveMessages((long)(timeout * 1000), TimeUnit.MILLISECONDS, returnOnTimeout, ignoreAttachments, this);
        } catch (IOException e) {
          if (sockets.size() == 0) {
            return;
          }
          logger.debug("disconnected from socket", e);
          if (backoff > 0) {
            this.sockets.broadcastListenStopped(e);
          }
        } catch (AssertionError e) {
          this.sockets.broadcastListenStopped(e);
          logger.catching(e);
        }
        if (m.getAccountData().isDeleted()) {
          return; // exit the receive thread
        }
        if (backoff == 0) {
          notifyOnConnect = false;
          logger.debug("reconnecting immediately");
          backoff = 1;
        } else {
          if (backoff < 60) {
            backoff = (int)(backoff * 1.5);
          }
          logger.warn("Disconnected from socket, reconnecting in " + backoff + " seconds");
          TimeUnit.SECONDS.sleep(backoff);
        }
      }
      logger.debug("shutting down message receiver for " + Util.redact(username));
    } catch (Exception e) {
      logger.error("shutting down message receiver for " + Util.redact(username), e);
      sockets.broadcastListenStopped(e);
    }
  }

  @Override
  public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
    backoff = 0;
    if (exception != null) {
      if (exception instanceof SelfSendException) {
        logger.debug("ignoring SelfSendException (see https://gitlab.com/signald/signald/-/issues/24)");
      } else if (exception instanceof DuplicateMessageException || exception.getCause() instanceof DuplicateMessageException) {
        logger.warn("ignoring DuplicateMessageException (see https://gitlab.com/signald/signald/-/issues/50): " + exception.toString());
      } else if (exception instanceof UntrustedIdentityException) {
        logger.debug("UntrustedIdentityException: " + exception.toString());
      } else if (exception instanceof InvalidMetadataMessageException) {
        logger.warn("Received invalid metadata in incoming message: " + exception.toString());
      } else if (exception instanceof ProtocolException) {
        logger.warn("ProtocolException thrown while receiving: " + exception.toString());
      } else if (exception instanceof InvalidMessageException) {
        logger.warn("InvalidMessageException thrown while receiving");
      } else {
        logger.error("Unexpected error while receiving incoming message! Please report this at " + BuildConfig.ERROR_REPORTING_URL, exception);
      }
      this.sockets.broadcastReceiveFailure(exception);
    } else {
      this.sockets.broadcastIncomingMessage(envelope, content);
    }
  }

  static class SocketManager {
    private final List<MessageEncoder> listeners = Collections.synchronizedList(new ArrayList<>());

    public synchronized void add(MessageEncoder b) { listeners.add(b); }

    public synchronized boolean remove(Socket b) {
      Iterator<MessageEncoder> i = listeners.iterator();
      while (i.hasNext()) {
        MessageEncoder r = i.next();
        if (r.equals(b)) {
          return listeners.remove(r);
        }
      }
      return false;
    }

    public synchronized int size() { return listeners.size(); }

    private void broadcast(broadcastMessage b) {
      for (MessageEncoder l : this.listeners) {
        if (l.isClosed()) {
          listeners.remove(l);
          continue;
        }
        try {
          b.broadcast(l);
        } catch (IOException e) {
          logger.warn("IOException while writing to client socket: " + e.getMessage());
        }
      }
    }

    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) { broadcast(r -> r.broadcastIncomingMessage(envelope, content)); }

    public void broadcastReceiveFailure(Throwable exception) { broadcast(r -> r.broadcastReceiveFailure(exception)); }

    public void broadcastListenStarted() { broadcast(MessageEncoder::broadcastListenStarted); }

    public void broadcastListenStopped(Throwable exception) { broadcast(r -> r.broadcastListenStopped(exception)); }

    private interface broadcastMessage { void broadcast(MessageEncoder r) throws IOException; }
  }
}
