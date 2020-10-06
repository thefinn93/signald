/*
 * Copyright (C) 2020 Finn Herzfeld
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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.security.Security;
import java.util.concurrent.ConcurrentHashMap;

@Command(name = BuildConfig.NAME, mixinStandardHelpOptions = true, version = BuildConfig.NAME + " " + BuildConfig.VERSION)
public class Main implements Runnable {

  public static void main(String[] args) { CommandLine.run(new Main(), System.err, args); }

  @Option(names = {"-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting.") private boolean verbose = false;

  @Option(names = {"-s", "--socket"}, description = "The path to the socket file") private String socket_path = "/var/run/signald/signald.sock";

  @Option(names = {"-d", "--data"}, description = "Data storage location") private String data_path = System.getProperty("user.home") + "/.config/signald";

  @Option(names = {"--dump-protocol"}, description = "print a machine-readable description of the client protocol to stdout and exit") private boolean dumpProtocol = false;

  private static final Logger logger = LogManager.getLogger();

  public void run() {
    if (verbose) {
      Configurator.setLevel(System.getProperty("log4j.logger"), Level.DEBUG);
    }

    logger.debug("Starting " + BuildConfig.NAME + " " + BuildConfig.VERSION);

    if (dumpProtocol) {
      try {
        System.out.println(JSONUtil.GetMapper().writeValueAsString(ProtocolDocumentor.GetProtocolDocumentation()));
        System.exit(0);
      } catch (JsonProcessingException e) {
        logger.catching(e);
      }
      System.exit(1);
    }
    try {
      // Workaround for BKS truststore
      Security.insertProviderAt(new SecurityProvider(), 1);
      Security.addProvider(new BouncyCastleProvider());

      SocketManager socketmanager = new SocketManager();
      ConcurrentHashMap<String, MessageReceiver> receivers = new ConcurrentHashMap<String, MessageReceiver>();

      // Spins up one thread per inbound connection to the control socket
      File socketFile = new File(socket_path);
      if (socketFile.exists()) {
        logger.debug("Deleting existing socket file");
        Files.delete(socketFile.toPath());
      }
      logger.info("Binding to socket " + socket_path);
      AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
      server.bind(new AFUNIXSocketAddress(socketFile));

      logger.debug("Using data folder " + data_path);

      Manager.setDataPath(data_path);
      AccountData.setDataPath(data_path);

      // Spins up one thread per registered signal number, listens for incoming messages
      File[] users = new File(data_path + "/data").listFiles();

      if (users == null) {
        logger.warn("No users are currently defined, you'll need to register or link to your existing signal account");
      }

      SignalProtocolLoggerProvider.setProvider(new ProtocolLogger());

      logger.info("Started " + BuildConfig.NAME + " " + BuildConfig.VERSION);

      while (!Thread.interrupted()) {
        try {
          Socket socket = server.accept();
          socketmanager.add(socket);

          // Kick off the thread to read input
          Thread socketHandlerThread = new Thread(new SocketHandler(socket, receivers), "socketlistener");
          socketHandlerThread.start();

        } catch (IOException e) {
          logger.catching(e);
        }
      }
    } catch (Exception e) {
      logger.catching(e);
      System.exit(1);
    }
  }
}
