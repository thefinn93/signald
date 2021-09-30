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

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.Empty;
import io.finn.signald.MessageReceiver;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.annotations.ProtocolType;
import io.finn.signald.annotations.Required;
import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.clientprotocol.Request;
import io.finn.signald.clientprotocol.RequestType;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;

@ProtocolType("subscribe")
@Doc("receive incoming messages. After making a subscribe request, incoming messages will be sent to the client encoded "
     + "as ClientMessageWrapper. Send an unsubscribe request or disconnect from the socket to stop receiving messages.")
public class SubscribeRequest implements RequestType<Empty> {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) @Doc("The account to subscribe to incoming message for") @Required public String account;

  @Override
  public Empty run(Request request) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError {
    UUID accountUUID;
    try {
      accountUUID = AccountsTable.getUUID(account);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException e) {
      throw new InternalError("error getting account UUID", e);
    }

    try {
      MessageReceiver.subscribe(accountUUID, new IncomingMessageEncoder(request.getSocket(), accountUUID));
    } catch (io.finn.signald.exceptions.NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (IOException | SQLException | InvalidKeyException e) {
      throw new InternalError("error subscribing", e);
    }
    return new Empty();
  }

  static class IncomingMessageEncoder implements MessageEncoder {
    private static final Logger logger = LogManager.getLogger();
    private final ObjectMapper mapper = JSONUtil.GetMapper();
    Socket socket;
    UUID account;

    IncomingMessageEncoder(Socket s, UUID a) {
      socket = s;
      account = a;
    }

    public void broadcast(ClientMessageWrapper w) throws IOException {
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      out.println(mapper.writeValueAsString(w));
    }

    @Override
    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws IOException {
      try {
        IncomingMessage message = new IncomingMessage(envelope, content, account);
        broadcast(new ClientMessageWrapper(message));
      } catch (NoSuchAccountError | ServerNotFoundError | InvalidProxyError | InternalError e) {
        logger.warn("Exception while broadcasting incoming message: " + e.toString());
      }
    }

    @Override
    public void broadcastReceiveFailure(Throwable exception) throws IOException {
      broadcast(ClientMessageWrapper.Exception(exception));
    }

    @Override
    public void broadcastListenStarted() throws IOException {
      broadcast(new ClientMessageWrapper(new ListenerState(true)));
    }

    @Override
    public void broadcastListenStopped(Throwable exception) throws IOException {
      broadcast(new ClientMessageWrapper(new ListenerState(false)));
      if (exception != null) {
        broadcast(ClientMessageWrapper.Exception(exception));
      }
    }

    @Override
    public boolean isClosed() {
      return socket.isClosed();
    }

    @Override
    public boolean equals(Socket s) {
      return socket.equals(s);
    }

    @Override
    public boolean equals(MessageEncoder encoder) {
      return encoder.equals(socket);
    }
  }
}
