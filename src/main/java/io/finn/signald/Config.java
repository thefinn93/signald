package io.finn.signald;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.finn.signald.clientprotocol.v1.JsonVersionMessage;
import io.finn.signald.clientprotocol.v1.ProtocolRequest;
import io.finn.signald.util.JSONUtil;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import io.sentry.Sentry;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.protocol.util.Pair;
import picocli.CommandLine;

public class Config {
  private static Logger logger = LogManager.getLogger();
  private static final String SYSTEM_SOCKET_PATH = "/var/run/signald/signald.sock";

  @CommandLine.Option(names = {"-V", "--version"}) private static boolean version = false;
  @CommandLine.Option(names = {"-v", "--verbose"}, description = "Verbose mode. Helpful for troubleshooting (env SIGNALD_VERBOSE_LOGGING)") private static boolean verbose = false;
  @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message") private static boolean usageHelpRequested;
  @CommandLine.Option(names = {"-s", "--socket"}, description = "The path to the socket file") private static String socketPath = null;
  @CommandLine.Option(names = {"-u", "--user-socket"},
                      description = "put the socket in the user runtime directory ($XDG_RUNTIME_DIR), the default unless --socket or --system-socket is specified")
  private static boolean userSocket = false;
  @CommandLine.Option(names = {"--system-socket"}, description = "make the socket file accessible system-wide") private static boolean systemSocket = false;
  @CommandLine.Option(names = {"-d", "--data"}, description = "Data storage location") private static String dataPath = System.getProperty("user.home") + "/.config/signald";
  @CommandLine.Option(names = {"--database"}, description = "jdbc connection string. Defaults to sqlite:~/.config/signald/signald.db (sqlite and postgres supported)")
  private static String db;
  @CommandLine.Option(names = {"--dump-protocol"}, description = "print a machine-readable description of the client protocol to stdout and exit "
                                                                 + "(https://signald.org/articles/protocol/documentation/)")
  private static boolean dumpProtocol = false;
  @CommandLine.Option(names = {"-m", "--metrics"}, description = "record and expose metrics in prometheus format (env SIGNALD_ENABLE_METRICS)")
  private static boolean metrics = false;
  @CommandLine.Option(names = {"--metrics-http-port"}, description = "metrics http listener port (env SIGNALD_METRICS_PORT)", defaultValue = "9595", paramLabel = "port")
  private static int metricsHttpPort;
  @CommandLine.Option(names = {"--log-http-requests"}, description = "log all requests send to the server. this is used for debugging but generally "
                                                                     + "should not be used otherwise (env SIGNALD_HTTP_LOGGING=true)")
  private static boolean logHttpRequests = false;
  @CommandLine.Option(names = {"--decrypt-timeout"}, description = "decryption timeout (in seconds). if signald detects that decryption has taken longer than this, "
                                                                   + "it will exit with code 101")
  private static int decryptionTimeout = 30;
  @CommandLine.Option(names = {"--trust-new-keys"}, description = "when a remote key changes, set trust level to TRUSTED_UNVERIFIED instead of UNTRUSTED "
                                                                  + "(env SIGNALD_TRUST_NEW_KEYS=true)")
  private static boolean trustNewKeys;
  @CommandLine.Option(names = {"--trust-all-keys-on-start"}, description = "mark all known keys as trusted on startup (env SIGNALD_TRUST_ALL_KEYS=true)")
  private static boolean trustAllKeys;
  @CommandLine.Option(names = {"--log-database-transactions"}, description = "log when DB transactions occur and how long they took. Note that db logs are at the debug "
                                                                             + "level, so --verbose should also be used. (env SIGNALD_LOG_DB_TRANSACTIONS=true)")
  private static boolean logDatabaseTransactions;

  public static void init() throws IOException {
    if (usageHelpRequested) {
      CommandLine.usage(new Config(), System.out);
      System.exit(2);
    }
    if(version) {
      System.out.println(JSONUtil.GetWriter().writeValueAsString(new JsonVersionMessage()));
      System.exit(0);
    }
    if (System.getenv("SIGNALD_VERBOSE_LOGGING") != null) {
      verbose = Boolean.parseBoolean(System.getenv("SIGNALD_VERBOSE_LOGGING"));
    }

    if (verbose) {
      if (System.getenv("SIGNALD_TRACE_LOGGING") != null && Boolean.parseBoolean(System.getenv("SIGNALD_TRACE_LOGGING"))) {
        Configurator.setLevel(System.getProperty("log4j.logger"), Level.TRACE);
      } else {
        Configurator.setLevel(System.getProperty("log4j.logger"), Level.DEBUG);
      }
      LogSetup.setup();
      logger.debug("Debug logging enabled");
    }

    if (Config.dumpProtocol) {
      try {
        System.out.println(JSONUtil.GetMapper().writeValueAsString(ProtocolRequest.GetProtocolDocumentation()));
        System.exit(0);
      } catch (JsonProcessingException | NoSuchMethodException e) {
        logger.catching(e);
        System.exit(1);
      }
    }

    if (System.getenv("SENTRY_DSN") != null) {
      Sentry.init(options -> {
        options.setDsn(System.getenv("SENTRY_DSN"));
        options.setRelease(BuildConfig.NAME + "@" + BuildConfig.VERSION);
      });
      logger.info("exception reporting via Sentry enabled");
    }

    if (System.getenv("SIGNALD_DATABASE") != null) {
      db = System.getenv("SIGNALD_DATABASE");
    }

    if (System.getenv("SIGNALD_HTTP_LOGGING") != null) {
      logHttpRequests = Boolean.parseBoolean(System.getenv("SIGNALD_HTTP_LOGGING"));
    }

    if (System.getenv("SIGNALD_LOG_DB_TRANSACTIONS") != null) {
      logDatabaseTransactions = Boolean.parseBoolean(System.getenv("SIGNALD_LOG_DB_TRANSACTIONS"));
    }

    if (System.getenv("SIGNALD_TRUST_NEW_KEYS") != null) {
      trustNewKeys = Boolean.parseBoolean(System.getenv("SIGNALD_TRUST_NEW_KEYS"));
    }

    if (trustNewKeys) {
      logger.info("new keys will be marked as TRUSTED_UNVERIFIED instead of UNTRUSTED");
    }

    if (System.getenv("SIGNALD_TRUST_ALL_KEYS") != null) {
      trustAllKeys = Boolean.parseBoolean(System.getenv("SIGNALD_TRUST_ALL_KEYS"));
    }

    if (System.getenv("SIGNALD_ENABLE_METRICS") != null) {
      metrics = Boolean.parseBoolean(System.getenv("SIGNALD_ENABLE_METRICS"));
    }

    if (metrics) {
      if (System.getenv("SIGNALD_METRICS_PORT") != null) {
        metricsHttpPort = Integer.parseInt(System.getenv("SIGNALD_METRICS_PORT"));
      }
      try {
        DefaultExports.initialize();
        logger.debug("starting metrics server on port {}", metricsHttpPort);
        new HTTPServer(metricsHttpPort);
      } catch (IOException e) {
        logger.error("error starting metrics server:", e);
        Sentry.captureException(e);
      }
    }

    if (db == null) {
      db = "sqlite:" + dataPath + "/signald.db";
    }

    if (socketPath == null) {
      // will be null if the environment variable is unset, in which case we will fall back to system mode
      String userDir = System.getenv("XDG_RUNTIME_DIR");
      if (userDir == null || Config.systemSocket) {
        socketPath = SYSTEM_SOCKET_PATH;
      } else {
        if (!userSocket) {
          logger.info("the default socket path has changed. For previous behavior, use --system-socket. See https://signald.org/articles/protocol/#socket-file-location");
        }

        Path userSocketDir = Paths.get(userDir, "signald");
        Files.createDirectories(userSocketDir);
        socketPath = Paths.get(userSocketDir.toString(), "signald.sock").toString();
      }
    }
  }

  public static void testInit(String testDb) throws IOException {
    db = testDb;
    init();
  }

  public static int getDecryptionTimeout() { return decryptionTimeout; }

  public static String getDataPath() { return dataPath; }

  public static boolean getLogHttpRequests() { return logHttpRequests; }

  private static Pair<String, String> getUserAndPassword() {
    URI uri = URI.create(db);
    var userInfoString = uri.getUserInfo();
    if (userInfoString == null) {
      return new Pair<>(null, null);
    }

    // Extract the user and password from the URI
    var userInfo = userInfoString.split(":");
    if (userInfo.length == 1) {
      return new Pair<>(userInfo[0], null);
    }

    if (userInfo.length == 2) {
      return new Pair<>(userInfo[0], userInfo[1]);
    }

    return new Pair<>(null, null);
  }

  public static String getDb() {
    URI uri = URI.create(db);

    String hostPort = "";
    if (uri.getHost() != null) {
      hostPort += "//" + uri.getHost();
      if (uri.getPort() >= 0)
        hostPort += ":" + uri.getPort();
    }

    String query = "";
    if (uri.getQuery() != null && uri.getQuery().length() > 0)
      query += "?" + uri.getQuery();

    // Re-combine without the user and password.
    return String.format("jdbc:%s:%s%s%s", uri.getScheme(), hostPort, uri.getPath(), query);
  }

  public static String getDbUser() { return getUserAndPassword().first(); }
  public static String getDbPassword() { return getUserAndPassword().second(); }

  public static String getSocketPath() { return socketPath; }

  public static TrustLevel getNewKeyTrustLevel() { return trustNewKeys ? TrustLevel.TRUSTED_UNVERIFIED : TrustLevel.UNTRUSTED; }

  public static boolean getLogDatabaseTransactions() { return logDatabaseTransactions; }

  public static boolean getTrustAllKeys() { return trustAllKeys; }
}
