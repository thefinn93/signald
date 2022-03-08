/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol;

import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

public interface MessageEncoder {
  void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws IOException, SQLException;
  void broadcastReceiveFailure(SignalServiceEnvelope envelope, Throwable exception) throws IOException;
  void broadcastListenStarted() throws IOException;
  void broadcastListenStopped(Throwable exception) throws IOException;
  void broadcastWebSocketConnectionStateChange(WebSocketConnectionState state, boolean unidentified) throws IOException;
  void broadcastStorageChange(long version) throws IOException;
  boolean isClosed();
  boolean equals(Socket socket);
  boolean equals(MessageEncoder encoder);
}
