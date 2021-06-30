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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.*;
import io.finn.signald.clientprotocol.v1.JsonVersionMessage;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.util.JSONUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

public class ClientConnection implements Runnable {
  private static final Logger logger = LogManager.getLogger();
  private final ObjectMapper mapper = JSONUtil.GetMapper();
  private final BufferedReader reader;
  private final PrintWriter writer;
  private final Socket socket;
  private final LegacySocketHandler legacySocketHandler;

  public ClientConnection(Socket s) throws IOException {
    reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
    writer = new PrintWriter(s.getOutputStream(), true);
    socket = s;
    legacySocketHandler = new LegacySocketHandler(socket);
  }

  @Override
  public void run() {
    logger.info("Client connected");
    try {
      JsonMessageWrapper message = new JsonMessageWrapper("version", new JsonVersionMessage(), (String)null);
      send(message);

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().length() > 0) {
          new Thread(new RequestRunner(line)).start();
        }
      }
    } catch (SocketException e) {
      logger.debug("socket exception while reading from client. Likely just means the client disconnected before we expected: " + e.getMessage());
    } catch (IOException e) {
      handleError(e, null);
    } finally {
      MessageReceiver.unsubscribeAll(socket);
      try {
        reader.close();
        writer.close();
      } catch (IOException e) {
        logger.catching(e);
      }
      logger.info("Client disconnected");
    }
  }

  private void handleError(Throwable error, JsonRequest request) {
    if (error instanceof NoSuchAccount) {
      logger.warn("unable to process request for non-existent account");
    } else if (error instanceof UnregisteredUserException) {
      logger.warn("failed to send to an address that is not on Signal (UnregisteredUserException)");
    } else {
      logger.catching(error);
    }
    String requestID = "";
    if (request != null) {
      requestID = request.id;
    }
    try {
      JsonMessageWrapper message = new JsonMessageWrapper("unexpected_error", new JsonStatusMessage(0, error.getMessage(), request), requestID);
      send(message);
    } catch (JsonProcessingException e) {
      logger.catching(e);
    }
  }

  public void send(JsonMessageWrapper message) throws JsonProcessingException {
    String m = mapper.writeValueAsString(message);
    synchronized (writer) { writer.println(m); }
  }

  private class RequestRunner implements Runnable {
    private final String line;

    RequestRunner(String l) { line = l; }

    @Override
    public void run() {
      JsonRequest request = null;
      String id = " ";
      try {
        JsonNode rawRequest = mapper.readTree(line);
        if (rawRequest.has("id")) {
          id = " (request ID: " + rawRequest.get("id").asText() + ") ";
        }
        String version = "v0";
        if (rawRequest.has("version")) {
          version = rawRequest.get("version").asText();
        } else if (rawRequest.has("type") && Request.defaultVersions.containsKey(rawRequest.get("type").asText())) {
          String type = rawRequest.get("type").asText();
          version = Request.defaultVersions.get(type);
        }

        if (!rawRequest.has("version")) {
          logger.info("signald received a request" + id + "with no version. This will stop working in a future version of signald. "
                      + "Please update your client. Client authors, see https://signald.org/articles/protocol-versioning/");
        }

        if (version.equals("v0")) {
          request = mapper.convertValue(rawRequest, JsonRequest.class);
          logger.debug("All v0 requests are deprecated and will be removed in late 2021. Client authors, "
                       + "see https://signald.org/articles/protocol-versioning/#deprecation. This message will become a "
                       + "warning in signald 0.15");
          legacySocketHandler.handleRequest(request);
        } else {
          new Request(rawRequest, socket);
        }
      } catch (Throwable e) {
        handleError(e, request);
      }
    }
  }
}
