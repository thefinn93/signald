/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.sentry.Sentry;
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
import org.signal.libsignal.protocol.*;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

public class MessageReceiver implements Manager.ReceiveMessageHandler, Runnable {
  private final ACI aci;
  private final Manager m;
  private int backoff = 0;
  private final SocketManager sockets;
  private final String uuid;
  private static final Logger logger = LogManager.getLogger();
  private static final HashMap<String, MessageReceiver> receivers = new HashMap<>();
  static final Gauge subscribedAccounts =
      Gauge.build().name(BuildConfig.NAME + "_subscribed_accounts").help("number of accounts subscribed to messages from the Signal server").register();
  static final Counter receivedMessages =
      Counter.build().name(BuildConfig.NAME + "_received_messages").help("number of messages received").labelNames("account_uuid", "error").register();

  public MessageReceiver(ACI aci) throws SQLException, IOException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    this.aci = aci;
    this.m = Manager.get(aci);
    this.uuid = m.getACI().toString();
    this.sockets = new SocketManager();
  }

  public static void subscribe(ACI aci, MessageEncoder receiver)
      throws SQLException, IOException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    synchronized (receivers) {
      if (!receivers.containsKey(aci.toString())) {
        MessageReceiver r = new MessageReceiver(aci);
        new Thread(r).start();
        receivers.put(aci.toString(), r);
      }
      receivers.get(aci.toString()).sockets.add(receiver);
    }
    logger.debug("message receiver for " + Util.redact(aci) + " got new subscriber. subscriber count: " + receivers.get(aci.toString()).sockets.size());
  }

  public static boolean unsubscribe(ACI aci, Socket s) {
    synchronized (receivers) {
      if (!receivers.containsKey(aci.toString())) {
        return false;
      }
      return synchronizedUnsubscribe(aci, s);
    }
  }

  public static void unsubscribeAll(Socket s) {
    synchronized (receivers) {
      for (String r : receivers.keySet()) {
        synchronizedUnsubscribe(ACI.from(UUID.fromString(r)), s);
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

  public static void handleWebSocketConnectionStateChange(UUID accountUUID, WebSocketConnectionState connectionState, boolean unidentified) throws SQLException {
    synchronized (receivers) {
      MessageReceiver receiver = receivers.get(accountUUID.toString());
      if (receiver == null) {
        return;
      }

      receiver.sockets.broadcastWebSocketConnectionStateChange(connectionState, unidentified);

      switch (connectionState) {
      case AUTHENTICATION_FAILED:
        receivers.get(accountUUID.toString()).sockets.removeAll();
        break;
      case CONNECTED:
        receiver.sockets.broadcastListenStarted();
        if (receiver.backoff != 0) {
          receiver.backoff = 0;
          logger.debug("websocket connected, resetting backoff");
        }
        break;
      }
    }
  }

  public static void broadcastStorageStateChange(UUID accountUUID, long version) throws SQLException {
    synchronized (receivers) {
      MessageReceiver receiver = receivers.get(accountUUID.toString());
      if (receiver == null) {
        return;
      }
      receiver.sockets.broadcastStorageStateChange(version);
    }
  }

  // must be called from within a synchronized(receivers) block
  private static boolean synchronizedUnsubscribe(ACI aci, Socket s) {
    if (!receivers.containsKey(aci.toString())) {
      return false;
    }

    boolean removed = receivers.get(aci.toString()).remove(s);
    if (removed) {
      logger.debug("message receiver for " + Util.redact(aci) + " lost a subscriber. subscriber count: " + receivers.get(aci.toString()).sockets.size());
    }
    if (removed && receivers.get(aci.toString()).sockets.size() == 0) {
      logger.info("Last client for " + Util.redact(aci) + " unsubscribed, shutting down message pipe");
      try {
        SignalDependencies.get(aci).getWebSocket().disconnect();
      } catch (IOException | SQLException | ServerNotFoundException | InvalidProxyException | NoSuchAccountException e) {
        logger.catching(e);
      }
      receivers.remove(aci.toString());
    }
    return removed;
  }

  private boolean remove(Socket socket) { return sockets.remove(socket); }

  public void run() {
    boolean notifyOnConnect = true;
    logger.debug("starting message receiver for " + Util.redact(aci));
    try {
      Thread.currentThread().setName(Util.redact(aci) + "-receiver");
      while (sockets.size() > 0) {
        double timeout = 3600;
        boolean returnOnTimeout = true;
        boolean ignoreAttachments = false;

        if (!Database.Get().AccountsTable.exists(aci)) {
          logger.info("account no longer exists, not (re)-connecting");
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
      logger.debug("shutting down message receiver for " + Util.redact(aci));
    } catch (Exception e) {
      logger.error("shutting down message receiver for " + Util.redact(aci), e);
      Sentry.captureException(e);
      try {
        sockets.broadcastListenStopped(e);
      } catch (SQLException ex) {
        logger.error("SQL exception occurred stopping listener");
        Sentry.captureException(e);
      }
    }
  }

  @Override
  public void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, Throwable exception) throws SQLException {
    if (exception != null) {
      if (exception instanceof SelfSendException) {
        logger.debug("ignoring SelfSendException (see https://gitlab.com/signald/signald/-/issues/24)");
      } else if (exception instanceof DuplicateMessageException || exception.getCause() instanceof DuplicateMessageException) {
        logger.warn("ignoring DuplicateMessageException (see https://gitlab.com/signald/signald/-/issues/50)", exception);
      } else if (exception instanceof UntrustedIdentityException) {
        logger.debug("UntrustedIdentityException", exception);
      } else if (exception instanceof InvalidMetadataMessageException) {
        logger.warn("Received invalid metadata in incoming message", exception);
      } else if (exception instanceof ProtocolException || exception.getCause() instanceof ProtocolException) {
        logger.warn("ProtocolException thrown while receiving", exception);
      } else if (exception instanceof InvalidMessageException) {
        logger.warn("InvalidMessageException thrown while receiving");
      } else if (exception instanceof InvalidKeyIdException) {
        logger.warn("InvalidKeyIdException while receiving: {}", exception.getMessage());
      } else {
        logger.error("Unexpected error while receiving incoming message! Please report this at " + BuildConfig.ERROR_REPORTING_URL, exception);
        Sentry.captureException(exception);
      }
      this.sockets.broadcastReceiveFailure(envelope, exception);
    } else {
      this.sockets.broadcastIncomingMessage(envelope, content);
    }
    String errorLabel = exception == null ? "" : exception.getClass().getCanonicalName();
    receivedMessages.labels(uuid, errorLabel).inc();
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

    private void broadcast(broadcastMessage b) throws SQLException {
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

    public void broadcastWebSocketConnectionStateChange(WebSocketConnectionState state, boolean unidentified) throws SQLException {
      broadcast(r -> r.broadcastWebSocketConnectionStateChange(state, unidentified));
    }

    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws SQLException {
      broadcast(r -> r.broadcastIncomingMessage(envelope, content));
    }

    public void broadcastReceiveFailure(SignalServiceEnvelope envelope, Throwable exception) throws SQLException { broadcast(r -> r.broadcastReceiveFailure(envelope, exception)); }

    public void broadcastListenStarted() throws SQLException { broadcast(MessageEncoder::broadcastListenStarted); }

    public void broadcastListenStopped(Throwable exception) throws SQLException { broadcast(r -> r.broadcastListenStopped(exception)); }

    public void broadcastStorageStateChange(long version) throws SQLException { broadcast(r -> r.broadcastStorageChange(version)); }

    private interface broadcastMessage {
      void broadcast(MessageEncoder r) throws IOException, SQLException;
    }
  }
}
