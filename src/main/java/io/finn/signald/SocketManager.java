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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        l.broadcastReceiveFailure(exception);
      } catch (IOException e) {
        logger.warn("IOException while writing to client socket: " + e.getMessage());
      }
    }
  }
}
