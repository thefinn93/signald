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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.*;
import io.finn.signald.clientprotocol.v1.JsonVersionMessage;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.util.JSONUtil;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.newsclub.net.unix.AFUNIXSocket;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;

public class ClientConnection implements Runnable {
  private static final Logger logger = LogManager.getLogger();
  private final ObjectMapper mapper = JSONUtil.GetMapper();
  private final Socket socket;
  private final LegacySocketHandler legacySocketHandler;
  static final Gauge clientsConnected = Gauge.build().name(BuildConfig.NAME + "_current_clients_connected").help("current client connections").register();
  static final Counter clientsConnectedTotal = Counter.build().name(BuildConfig.NAME + "_clients_connected_total").help("total client connections").register();
  static final Summary requestProcessingTime = Summary.build()
                                                   .quantile(0.5, 0.05)
                                                   .quantile(0.9, 0.01)
                                                   .name(BuildConfig.NAME + "_request_processing_time")
                                                   .help("Time (in seconds) to process requests.")
                                                   .labelNames("request_type", "request_version")
                                                   .register();
  static final Counter requestCount = Counter.build().name(BuildConfig.NAME + "_requests_total").help("Total requests processed").labelNames("request_type", "version").register();

  public ClientConnection(Socket s) throws IOException {
    socket = s;
    legacySocketHandler = new LegacySocketHandler(socket);
  }

  @Override
  public void run() {
    logger.info("Client connected");

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
      try (PrintWriter w = new PrintWriter(socket.getOutputStream(), true)) {
        try {
          clientsConnectedTotal.inc();
          clientsConnected.inc();
          JsonMessageWrapper message = new JsonMessageWrapper("version", new JsonVersionMessage(), (String)null);
          send(w, message);

          String line;
          while ((line = reader.readLine()) != null) {
            if (line.trim().length() > 0) {
              new Thread(new RequestRunner(line, w)).start();
            }
          }
        } catch (IOException e) {
          handleError(w, e, null);
        }
      }
    } catch (IOException e) {
      logger.debug("client socket exception: " + e.getMessage());
    } finally {
      MessageReceiver.unsubscribeAll(socket);
      logger.info("Client disconnected");
      clientsConnected.dec();
    }
  }

  private void handleError(PrintWriter w, Throwable error, JsonRequest request) {
    if (error instanceof NoSuchAccountError) {
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
      send(w, message);
    } catch (IOException e) {
      logger.catching(e);
    }
  }

  public void send(PrintWriter w, JsonMessageWrapper message) throws IOException {
    String m = mapper.writeValueAsString(message);
    synchronized (socket) { w.println(m); }
  }

  private class RequestRunner implements Runnable {
    private final String line;
    private final PrintWriter writer;

    RequestRunner(String l, PrintWriter w) {
      line = l;
      writer = w;
    }

    @Override
    public void run() {
      JsonRequest request = null;
      String id = " ";
      try {
        JsonNode rawRequest = mapper.readTree(line);
        if (rawRequest.has("id")) {
          id = " (request ID: " + rawRequest.get("id").asText() + ") ";
          logger.debug("handling request with ID: " + rawRequest.get("id").asText());
        }
        String version = "v0";
        String type = rawRequest.get("type").asText();
        if (rawRequest.has("version")) {
          version = rawRequest.get("version").asText();
        } else if (rawRequest.has("type") && Request.defaultVersions.containsKey(rawRequest.get("type").asText())) {
          version = Request.defaultVersions.get(type);
        }
        if (rawRequest.has("id")) {
          logger.debug("request " + rawRequest.get("id").asText() + " has version " + version);
        }

        Summary.Timer timer = requestProcessingTime.labels(type, version).startTimer();
        try {
          if (!rawRequest.has("version")) {
            logger.info("signald received a request" + id + "with no version. This will stop working in a future version of signald. "
                        + "Please update your client. Client authors, see https://signald.org/articles/protocol-versioning/");
          }

          if (version.equals("v0")) {
            request = mapper.convertValue(rawRequest, JsonRequest.class);
            String client = "";
            if (socket instanceof AFUNIXSocket) {
              AFUNIXSocket unixsocket = (AFUNIXSocket)socket;
              client = unixsocket.getPeerCredentials().toString();
            }
            logger.warn("client " + client + " sent a v0 " + type + " request. v0 support will be removed at the end of "
                        + "2021. Please update your signald client. Client authors, see "
                        + "https://signald.org/articles/protocol-versioning/#deprecation");
            legacySocketHandler.handleRequest(request);
          } else {
            new Request(rawRequest, socket);
          }
        } finally {
          timer.observeDuration();
          requestCount.labels(type, version).inc();
        }
      } catch (Throwable e) {
        handleError(writer, e, request);
      }
    }
  }
}
