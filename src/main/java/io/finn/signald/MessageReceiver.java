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
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.ProtocolException;
import org.signal.libsignal.metadata.SelfSendException;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

public class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
  final UUID account;
  private final Manager m;
  private int backoff = 0;
  private final SocketManager sockets;
  private final String uuid;
  private static final Logger logger = LogManager.getLogger();
  private static final HashMap<String, MessageReceiver> receivers = new HashMap<>();
  static final Gauge subscribedAccounts =
      Gauge.build().name(BuildConfig.NAME + "_subscribed_accounts").help("number of accounts subscribed to messages from the Signal server").register();
  static final Counter receivedMessages = Counter.build().name(BuildConfig.NAME + "_received_messages").help("number of messages received").labelNames("account_uuid").register();

  public MessageReceiver(UUID account) throws SQLException, IOException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    this.account = account;
    this.m = Manager.get(account);
    this.uuid = m.getUUID().toString();
    this.sockets = new SocketManager();
  }

  public static void subscribe(UUID account, MessageEncoder receiver)
      throws SQLException, IOException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    synchronized (receivers) {
      if (!receivers.containsKey(account.toString())) {
        MessageReceiver r = new MessageReceiver(account);
        new Thread(r).start();
        receivers.put(account.toString(), r);
      }
      receivers.get(account.toString()).sockets.add(receiver);
    }
    logger.debug("message receiver for " + Util.redact(account) + " got new subscriber. subscriber count: " + receivers.get(account.toString()).sockets.size());
  }

  public static boolean unsubscribe(UUID account, Socket s) {
    synchronized (receivers) {
      if (!receivers.containsKey(account.toString())) {
        return false;
      }
      return synchronizedUnsubscribe(account, s);
    }
  }

  public static void unsubscribeAll(Socket s) {
    synchronized (receivers) {
      for (String r : receivers.keySet()) {
        synchronizedUnsubscribe(UUID.fromString(r), s);
      }
    }
  }

  public static void unsubscribeAll(UUID account) {
    synchronized (receivers) {
      if (!receivers.containsKey(account.toString())) {
        return;
      }
      receivers.get(account.toString()).sockets.removeAll();
    }
  }

  public static void handleWebSocketConnectionStateChange(UUID accountUUID, WebSocketConnectionState connectionState, boolean unidentified) {
    synchronized (receivers) {
      MessageReceiver receiver = receivers.get(accountUUID.toString());
      if (receiver == null) {
        return;
      }

      if (connectionState == WebSocketConnectionState.CONNECTED) {
        receiver.sockets.broadcastListenStarted();
        if (receiver.backoff != 0) {
          receiver.backoff = 0;
          logger.debug("websocket connected, resetting backoff");
        }
      }

      receiver.sockets.broadcastWebSocketConnectionStateChange(connectionState, unidentified);
    }
  }

  // must be called from within a sychronized(receivers) block
  private static boolean synchronizedUnsubscribe(UUID account, Socket s) {
    if (!receivers.containsKey(account.toString())) {
      return false;
    }

    boolean removed = receivers.get(account.toString()).remove(s);
    if (removed) {
      logger.debug("message receiver for " + Util.redact(account) + " lost a subscriber. subscriber count: " + receivers.get(account.toString()).sockets.size());
    }
    if (removed && receivers.get(account.toString()).sockets.size() == 0) {
      logger.info("Last client for " + Util.redact(account) + " unsubscribed, shutting down message pipe");
      try {
        SignalDependencies.get(account).getWebSocket().disconnect();
      } catch (IOException | SQLException | ServerNotFoundException | InvalidProxyException | NoSuchAccountException e) {
        logger.catching(e);
      }
      receivers.remove(account.toString());
    }
    return removed;
  }

  private boolean remove(Socket socket) { return sockets.remove(socket); }

  public void run() {
    boolean notifyOnConnect = true;
    logger.debug("starting message receiver for " + Util.redact(account));
    try {
      Thread.currentThread().setName(Util.redact(account) + "-receiver");
      while (sockets.size() > 0) {
        double timeout = 3600;
        boolean returnOnTimeout = true;
        boolean ignoreAttachments = false;

        if (!AccountsTable.exists(account)) {
          logger.info("account no longer exists, not re-connecting");
          break;
        }

        try {
          subscribedAccounts.inc();
          if (notifyOnConnect) {
            this.sockets.broadcastListenStarted();
          } else {
            notifyOnConnect = true;
          }
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
        } finally {
          subscribedAccounts.dec();
        }
        if (m.getAccountData().isDeleted()) {
          return; // exit the receive thread
        }
        if (backoff == 0) {
          notifyOnConnect = false;
          logger.debug("reconnecting immediately");
          backoff = 1;
        } else {
          if (backoff < 65) {
            backoff = backoff * 2;
          }
          logger.warn("Disconnected from socket, reconnecting in " + backoff + " seconds");
          TimeUnit.SECONDS.sleep(backoff);
        }
      }
      logger.debug("shutting down message receiver for " + Util.redact(account));
    } catch (Exception e) {
      logger.error("shutting down message receiver for " + Util.redact(account), e);
      sockets.broadcastListenStopped(e);
    }
  }

  @Override
  public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) {
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
    receivedMessages.labels(uuid).inc();
  }

  static class SocketManager {
    private final List<MessageEncoder> listeners = Collections.synchronizedList(new ArrayList<>());

    public synchronized void add(MessageEncoder b) {
      synchronized (listeners) {
        Iterator<MessageEncoder> i = listeners.iterator();
        while (i.hasNext()) {
          MessageEncoder r = i.next();
          if (r.equals(b)) {
            logger.debug("ignoring duplicate subscribe request");
            return;
          }
        }
        listeners.add(b);
      }
    }

    public synchronized boolean remove(Socket b) {
      synchronized (listeners) {
        Iterator<MessageEncoder> i = listeners.iterator();
        while (i.hasNext()) {
          MessageEncoder r = i.next();
          if (r.equals(b)) {
            return listeners.remove(r);
          }
        }
      }
      return false;
    }

    public synchronized void removeAll() {
      synchronized (listeners) { listeners.removeAll(listeners); }
    }

    public synchronized int size() { return listeners.size(); }

    private void broadcast(broadcastMessage b) {
      synchronized (listeners) {
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
    }

    public void broadcastWebSocketConnectionStateChange(WebSocketConnectionState state, boolean unidentified) {
      broadcast(r -> r.broadcastWebSocketConnectionStateChange(state, unidentified));
    }

    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) { broadcast(r -> r.broadcastIncomingMessage(envelope, content)); }

    public void broadcastReceiveFailure(Throwable exception) { broadcast(r -> r.broadcastReceiveFailure(exception)); }

    public void broadcastListenStarted() { broadcast(MessageEncoder::broadcastListenStarted); }

    public void broadcastListenStopped(Throwable exception) { broadcast(r -> r.broadcastListenStopped(exception)); }

    private interface broadcastMessage {
      void broadcast(MessageEncoder r) throws IOException;
    }
  }
}
