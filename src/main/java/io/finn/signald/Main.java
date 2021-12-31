/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.clientprotocol.ClientConnection;
import io.finn.signald.clientprotocol.v1.ProtocolRequest;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.Database;
import io.finn.signald.db.ServersTable;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.storage.AccountData;
import io.finn.signald.util.JSONUtil;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.asamk.signal.util.SecurityProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateOutput;
import org.flywaydb.core.api.output.MigrateResult;
import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;
import org.newsclub.net.unix.AFUNIXSocketCredentials;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = BuildConfig.NAME, mixinStandardHelpOptions = true, version = BuildConfig.NAME + " " + BuildConfig.VERSION)
public class Main implements Runnable {
  private static final String SYSTEM_SOCKET_PATH = "/var/run/signald/signald.sock";

  public static void main(String[] args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Option(names = {"-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting.") private boolean verbose = false;

  @Option(names = {"-s", "--socket"}, description = "The path to the socket file") private String socket_path = null;

  @Option(names = {"-u", "--user-socket"},
          description = "put the socket in the user runtime directory ($XDG_RUNTIME_DIR), the default unless --socket or --system-socket is specified")
  private boolean user_socket = false;

  @Option(names = {"--system-socket"}, description = "make the socket file accessible system-wide") private boolean system_socket = false;

  @Option(names = {"-d", "--data"}, description = "Data storage location") private String data_path = System.getProperty("user.home") + "/.config/signald";

  @Option(names = {"--database"}, description = "jdbc connection string. Defaults to jdbc:sqlite:~/.config/signald/signald.db. Only sqlite is supported at this time.")
  private String db;

  @Option(names = {"--dump-protocol"},
          description = "print a machine-readable description of the client protocol to stdout and exit (https://signald.org/articles/protocol/documentation/)")
  private boolean dumpProtocol = false;

  @Option(names = {"-m", "--metrics"}, description = "record and expose metrics in prometheus format") private boolean metrics = false;

  @Option(names = {"--metrics-http-port"}, description = "metrics http listener port", defaultValue = "9595", paramLabel = "port") private int metricsHttpPort;

  @Option(names = {"--log-http-requests"}, description = "log all requests send to the server. this is used for debugging but generally should not be used otherwise.")
  private boolean logHttpRequests = false;

  @Option(names = {"--decrypt-timeout"}, description = "decryption timeout (in seconds). if signald detects that decryption has taken longer than this, it will exit with code 101")
  private int decryptionTimeout = 10;

  private static final Logger logger = LogManager.getLogger();

  public void run() {
    if (verbose) {
      Configurator.setLevel(System.getProperty("log4j.logger"), Level.DEBUG);
      LogSetup.setup();
    }

    logger.debug("Starting " + BuildConfig.NAME + " " + BuildConfig.VERSION);

    if (getJavaVersion() < 11) {
      logger.warn("Support for this version of Java may be going away. Please update your java version. For more information see https://gitlab.com/signald/signald/-/issues/219");
    }

    if (dumpProtocol) {
      try {
        System.out.println(JSONUtil.GetMapper().writeValueAsString(ProtocolRequest.GetProtocolDocumentation()));
        System.exit(0);
      } catch (JsonProcessingException | NoSuchMethodException e) {
        logger.catching(e);
        System.exit(1);
      }
    }

    String enableHttpLogging = System.getenv("SIGNALD_HTTP_LOGGING");
    if (enableHttpLogging != null) {
      logHttpRequests = Boolean.parseBoolean(enableHttpLogging);
    }

    if (logHttpRequests) {
      ServersTable.setLogHttpRequests(true);
    }

    String enableMetrics = System.getenv("SIGNALD_ENABLE_METRICS");
    if (enableMetrics != null) {
      metrics = Boolean.parseBoolean(enableMetrics);
    }

    if (metrics) {
      String port = System.getenv("SIGNALD_METRICS_PORT");
      if (port != null) {
        metricsHttpPort = Integer.parseInt(port);
      }
      try {
        DefaultExports.initialize();
        logger.debug("starting metrics server on port " + metricsHttpPort);
        new HTTPServer(metricsHttpPort);
      } catch (IOException e) {
        logger.error("error starting metrics server:", e);
      }
    }

    try {
      // Workaround for BKS truststore
      Security.insertProviderAt(new SecurityProvider(), 1);
      Security.addProvider(new BouncyCastleProvider());

      logger.debug("Using data folder " + data_path);

      Manager.setDataPath(data_path);
      Manager.setDecryptionTimeout(decryptionTimeout);
      AccountData.setDataPath(data_path);

      if (db == null) {
        db = "jdbc:sqlite:" + data_path + "/signald.db";
        Manager.createPrivateDirectories(data_path);
      }

      sdnotify("STATUS=migrating database " + db);
      Flyway flyway = Flyway.configure().dataSource(db, null, null).load();
      MigrateResult migrateResult = flyway.migrate();
      for (String w : migrateResult.warnings) {
        logger.warn("db migration warning: " + w);
      }
      for (MigrateOutput o : migrateResult.migrations) {
        String message = "applied migration " + o.version + "/" + migrateResult.targetSchemaVersion + ": " + o.description + " [" + o.executionTime + " ms]";
        logger.info(message);
        sdnotify("STATUS=" + message);
      }

      Database.setConnectionString(db);

      // Migrate data as supported from the JSON state files:
      File[] allAccounts = new File(data_path + "/data").listFiles();
      if (allAccounts != null) {
        Pattern e164Pattern = Pattern.compile("^\\+?[1-9]\\d{1,14}$");
        for (File f : allAccounts) {
          if (f.isDirectory()) {
            continue;
          }
          if (e164Pattern.matcher(f.getName()).matches()) {
            AccountsTable.importFromJSON(f);
          } else {
            logger.warn("account file " + f.getAbsolutePath() + " does NOT appear to have a valid phone number in the filename!");
          }
        }
      }

      new Thread(new BackgroundJobRunnerThread()).start();

      if (socket_path == null) {
        // will be null if the environment variable is unset, in which case we will fall back to system mode
        String userDir = System.getenv("XDG_RUNTIME_DIR");
        if (userDir == null || system_socket) {
          socket_path = SYSTEM_SOCKET_PATH;
        } else {
          if (!user_socket) {
            logger.info("the default socket path has changed. For previous behavior, use --system-socket. See https://signald.org/articles/protocol/#socket-file-location");
          }

          Path userSocketDir = Paths.get(userDir, "signald");
          Files.createDirectories(userSocketDir);
          socket_path = Paths.get(userSocketDir.toString(), "signald.sock").toString();
        }
      }

      // Spins up one thread per inbound connection to the control socket
      File socketFile = new File(socket_path);
      if (socketFile.exists()) {
        logger.debug("Deleting existing socket file");
        Files.delete(socketFile.toPath());
      }

      logger.info("Binding to socket " + socket_path);
      AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
      try {
        server.bind(new AFUNIXSocketAddress(socketFile));
      } catch (SocketException e) {
        logger.fatal("Error creating socket at " + socketFile + ": " + e.getMessage());
        System.exit(1);
      }

      SignalProtocolLoggerProvider.setProvider(new ProtocolLogger());

      logger.info("Started " + BuildConfig.NAME + " " + BuildConfig.VERSION);
      sdnotify("READY=1");

      while (!Thread.interrupted()) {
        try {
          AFUNIXSocket socket = server.accept();
          AFUNIXSocketCredentials credentials = socket.getPeerCredentials();
          logger.debug("Connection from pid " + credentials.getPid() + " uid " + credentials.getUid());
          new Thread(new ClientConnection(socket), "connection-pid-" + credentials.getPid()).start();
        } catch (IOException e) {
          logger.catching(e);
        }
      }
    } catch (Exception e) {
      sdnotify("STATUS=" + e.getMessage());
      logger.catching(e);
      System.exit(1);
    }
  }

  // sdnotify is based on https://gist.github.com/yrro/18dc22513f1001d0ec8d
  public static void sdnotify(String arg) {
    try {
      String notifySocket = System.getenv("NOTIFY_SOCKET");
      if (notifySocket == null || !Files.isDirectory(Paths.get("/run/systemd/system"), LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      Process p = new ProcessBuilder("systemd-notify", arg).redirectErrorStream(true).start();
      if (ignoreInterruptedException(p::waitFor) == 0)
        return;

      logger.error("Failed to notify systemd of/that {}; systemd-notify exited with status {}", arg, p.exitValue());
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        r.lines().forEach(l -> logger.error("systemd-notify: {}", l));
      }
    } catch (IOException e) {
      logger.debug("Exception while notifying socket manager: " + e.getMessage());
    }
  }

  private interface ThrowingSupplier<T, E extends Throwable> {
    T get() throws E;
  }

  private static <T> T ignoreInterruptedException(ThrowingSupplier<T, InterruptedException> r) {
    for (;;) {
      try {
        return r.get();
      } catch (InterruptedException ignored) {
      }
    }
  }

  private static int getJavaVersion() {
    String version = System.getProperty("java.version");
    if (version.startsWith("1.")) {
      version = version.substring(2, 3);
    } else {
      int dot = version.indexOf(".");
      if (dot != -1) {
        version = version.substring(0, dot);
      }
    }
    return Integer.parseInt(version);
  }
}
