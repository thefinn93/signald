/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

// TODO: can we delete this entire thing?
class SocketManager {
  private final static ObjectMapper mapper = JSONUtil.GetMapper();
  private static final Logger logger = LogManager.getLogger();

  private final List<MessageEncoder> listeners = Collections.synchronizedList(new ArrayList<>());

  public synchronized void add(MessageEncoder b) { this.listeners.add(b); }

  public synchronized boolean remove(MessageEncoder b) { return this.listeners.remove(b); }

  public synchronized int size() { return this.listeners.size(); }

  public void send(JsonMessageWrapper message, Socket s) throws IOException {
    String JSONMessage = mapper.writeValueAsString(message);
    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
    out.println(JSONMessage);
  }

  public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) {
    for (MessageEncoder l : this.listeners) {
      if (l.isClosed()) {
        this.remove(l);
        continue;
      }
      try {
        l.broadcastIncomingMessage(envelope, content);
      } catch (IOException e) {
        logger.warn("IOException while writing to client socket: " + e.getMessage());
      }
    }
  }

  public void broadcastReceiveFailure(Throwable exception) {
    for (MessageEncoder l : this.listeners) {
      if (l.isClosed()) {
        this.remove(l);
        continue;
      }
      try {
        l.broadcastReceiveFailure(null, exception);
      } catch (IOException e) {
        logger.warn("IOException while writing to client socket: " + e.getMessage());
      }
    }
  }
}
