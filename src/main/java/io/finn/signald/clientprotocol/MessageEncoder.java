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

package io.finn.signald.clientprotocol;

import java.io.IOException;
import java.net.Socket;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

public interface MessageEncoder {
  void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws IOException;
  void broadcastReceiveFailure(Throwable exception) throws IOException;
  void broadcastListenStarted() throws IOException;
  void broadcastListenStopped(Throwable exception) throws IOException;
  boolean isClosed();
  boolean equals(Socket socket);
  boolean equals(MessageEncoder encoder);
}
