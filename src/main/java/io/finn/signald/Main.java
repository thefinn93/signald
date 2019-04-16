/**
 * Copyright (C) 2018 Finn Herzfeld
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

import io.finn.signald.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.Security;
import java.util.concurrent.ConcurrentHashMap;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import org.asamk.signal.util.SecurityProvider;

import io.sentry.Sentry;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name=BuildConfig.NAME, mixinStandardHelpOptions=true, version=BuildConfig.NAME + " " + BuildConfig.VERSION)
public class Main implements Runnable {

  public static void main(String[] args) {
    CommandLine.run(new Main(), System.err, args);
  }

  @Option(names={"-v", "--verbose"}, description="Verbose mode. Helpful for troubleshooting.")
  private boolean verbose = false;

  @Option(names={"-s", "--socket"}, description="The path to the socket file")
  private String socket_path = "/var/run/signald/signald.sock";

  @Option(names={"-d", "--data"}, description="Data storage location")
  private String data_path = System.getProperty("user.home") + "/.config/signald";

  private static final Logger logger = LogManager.getLogger("signald");

  public void run() {
    Logger logger = LogManager.getLogger("signald");
    if(verbose) {
      Configurator.setLevel(System.getProperty("log4j.logger"), Level.DEBUG);
    }

    logger.debug("Starting " + BuildConfig.NAME + " " + BuildConfig.VERSION);

    try {
      Sentry.init();
      Sentry.getContext().addExtra("release", BuildConfig.VERSION);
      Sentry.getContext().addExtra("signal_url", BuildConfig.SIGNAL_URL);
      Sentry.getContext().addExtra("signal_cdn_url", BuildConfig.SIGNAL_CDN_URL);

      // Workaround for BKS truststore
      Security.insertProviderAt(new SecurityProvider(), 1);
      Security.addProvider(new BouncyCastleProvider());

      SocketManager socketmanager = new SocketManager();
      ConcurrentHashMap<String,Manager> managers = new ConcurrentHashMap<String,Manager>();
      ConcurrentHashMap<String,MessageReceiver> receivers = new ConcurrentHashMap<String,MessageReceiver>();

      logger.info("Binding to socket " + socket_path);

      // Spins up one thread per inbound connection to the control socket
      AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
      server.bind(new AFUNIXSocketAddress(new File(socket_path)));

      // Spins up one thread per registered signal number, listens for incoming messages
      File[] users = new File(data_path + "/data").listFiles();

      if(users == null) {
         logger.warn("No users are currently defined, you'll need to register or link to your existing signal account");
      }

      logger.debug("Using data folder " + data_path);

      logger.info("Started " + BuildConfig.NAME + " " + BuildConfig.VERSION);

      while (!Thread.interrupted()) {
        try {
          Socket socket = server.accept();
          socketmanager.add(socket);

          // Kick off the thread to read input
          Thread socketHandlerThread = new Thread(new SocketHandler(socket, receivers, managers, data_path), "socketlistener");
          socketHandlerThread.start();

        } catch(IOException e) {
          logger.catching(e);
        }
      }
    } catch(Exception e) {
      logger.catching(e);
      System.exit(1);
    }
  }
}
