package io.finn.signald.db;

import io.finn.signald.Config;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.flywaydb.core.Flyway;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class TestUtil {
  private static final String testPostgresDatabase = System.getenv("SIGNALD_POSTGRES_TEST_DATABASE");

  public static class TestPostgresDatabase {
    public SignalServiceAddress selfAddress;
    public String testHash;

    public TestPostgresDatabase(SignalServiceAddress selfAddress, String testHash) {
      this.selfAddress = selfAddress;
      this.testHash = testHash;
    }

    public ACI getAci() { return ACI.from(selfAddress.getServiceId().uuid()); }
  }

  public static TestPostgresDatabase createAndConfigureTestPostgresDatabase() throws IOException, SQLException {
    // Create a random phone number
    var random = ThreadLocalRandom.current();
    long number = 10000000000L + ((long)random.nextInt(900000000) * 100) + random.nextInt(100);
    var selfAddress = new SignalServiceAddress(ACI.from(UUID.randomUUID()), "+" + number);

    // Create a random hash for the test database and user.
    UUID testUUID = UUID.randomUUID();
    var testHash = "signald" + testUUID.toString().replace("-", "");

    // Create the database and user
    Config.testInit(testPostgresDatabase);
    try (var connection = Database.getConn()) {
      try (var statement = connection.prepareStatement("CREATE DATABASE " + testHash)) {
        statement.execute();
      }
      try (var statement = connection.prepareStatement(String.format("CREATE USER %s WITH PASSWORD 'test'", testHash))) {
        statement.execute();
      }
    }
    Database.close();

    // Replace the credentials in the test postgres database URI with then newly created user's credentials.
    URI uri = URI.create(testPostgresDatabase);
    String hostPort = "";
    if (uri.getHost() != null) {
      hostPort += String.format("//%s:test@%s", testHash, uri.getHost());
      if (uri.getPort() >= 0)
        hostPort += ":" + uri.getPort();
    }

    String query = "";
    if (uri.getQuery() != null && uri.getQuery().length() > 0) {
      query += "?" + uri.getQuery();
    }

    Config.testInit(String.format("%s:%s/%s%s", uri.getScheme(), hostPort, testHash, query));

    // Migrate the database.
    Flyway flyway = Flyway.configure().locations("db/migration/postgresql").dataSource(Config.getDb(), Config.getDbUser(), Config.getDbPassword()).load();
    flyway.migrate();

    return new TestPostgresDatabase(selfAddress, testHash);
  }

  public static void cleanupTestPostgresDatabase(TestPostgresDatabase testDb) throws IOException {
    Config.testInit(testPostgresDatabase);
    try (var connection = Database.getConn()) {
      try (var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE " + testDb.testHash);
      }
      try (var statement = connection.prepareStatement("DROP USER " + testDb.testHash)) {
        statement.execute();
      }
    } catch (SQLException e) {
      e.printStackTrace();
      System.err.println("Couldn't drop test database");
    }
    Database.close();
  }

  public static File createAndConfigureTestSQLiteDatabase() throws IOException {
    // Create the database file
    var tmpDirectory = new File(System.getProperty("java.io.tmpdir"));
    var databaseFile = File.createTempFile("test", "sqlite", tmpDirectory);
    String db = "sqlite:" + databaseFile.getAbsolutePath();

    // Configure the database
    Config.testInit(db);

    // Migrate the database
    Flyway flyway = Flyway.configure().locations("db/migration/sqlite").dataSource(Config.getDb(), null, null).load();
    flyway.migrate();

    return databaseFile;
  }
}
