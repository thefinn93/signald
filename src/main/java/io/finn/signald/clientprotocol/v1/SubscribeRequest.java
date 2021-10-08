/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
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
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.UntrustedIdentityException;
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
      MessageReceiver.subscribe(accountUUID, new IncomingMessageEncoder(request.getSocket(), accountUUID, account));
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
    UUID accountUUID;
    String account; // account identifier is still e164 for now, so that needs to be stored separately from the UUID

    private static final HashMap<Class<? extends Exception>, Class<? extends ExceptionWrapper>> exceptions = new HashMap<>();
    static {
      exceptions.put(ProtocolInvalidMessageException.class, ProtocolInvalidMessageError.class);
      exceptions.put(DuplicateMessageException.class, DuplicateMessageError.class);
      exceptions.put(org.whispersystems.signalservice.api.crypto.UntrustedIdentityException.class, UntrustedIdentityError.class);
      exceptions.put(UntrustedIdentityException.class, UntrustedIdentityError.class);
    }

    IncomingMessageEncoder(Socket socket, UUID accountUUID, String account) {
      this.socket = socket;
      this.accountUUID = accountUUID;
      this.account = account;
    }

    public void broadcast(ClientMessageWrapper w) throws IOException {
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      out.println(mapper.writeValueAsString(w));
    }

    @Override
    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws IOException {
      try {
        IncomingMessage message = new IncomingMessage(envelope, content, accountUUID);
        broadcast(new ClientMessageWrapper(account, message));
      } catch (NoSuchAccountError | ServerNotFoundError | InvalidProxyError | InternalError e) {
        logger.warn("Exception while broadcasting incoming message: " + e.toString());
      }
    }

    @Override
    public void broadcastReceiveFailure(Throwable exception) throws IOException {
      broadcast(getError(exception));
    }

    @Override
    public void broadcastListenStarted() throws IOException {
      broadcast(new ClientMessageWrapper(account, new ListenerState(true)));
    }

    @Override
    public void broadcastListenStopped(Throwable exception) throws IOException {
      broadcast(new ClientMessageWrapper(account, new ListenerState(false)));
      if (exception != null) {
        broadcast(getError(exception));
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

    private ClientMessageWrapper getError(Throwable exception) {
      ExceptionWrapper error;
      try {
        if (exceptions.containsKey(exception.getClass())) {
          Class<? extends ExceptionWrapper> errorType = exceptions.get(exception.getClass());
          Constructor<? extends ExceptionWrapper> constructor;
          try {
            constructor = errorType.getDeclaredConstructor(exception.getClass());
            error = constructor.newInstance(exception);
          } catch (NoSuchMethodException ignored) {
            constructor = errorType.getDeclaredConstructor(UUID.class, exception.getClass());
            error = constructor.newInstance(accountUUID, exception);
          }
        } else {
          error = new InternalError("unexpected error while receiving", exception);
        }
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        logger.error("fatal error rendering error response: ", e);
        error = new InternalError("unexpected error while receiving", exception);
      }
      ClientMessageWrapper wrapper = new ClientMessageWrapper(account, error);
      wrapper.error = true;
      return wrapper;
    }
  }
}
