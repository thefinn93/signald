package io.finn.signald.db.migrations.postgresql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.Account;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.UnregisteredUserError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IServersTable;
import io.finn.signald.db.TestUtil;
import io.finn.signald.storage.LegacyContactStore;
import io.finn.signald.util.JSONUtil;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

public class ContactsTableMigrationTest {
  private TestUtil.TestPostgresDatabase testDatabase;

  @BeforeEach
  void setUp() throws IOException, SQLException {
    testDatabase = TestUtil.createAndConfigureTestPostgresDatabase();

    // Create the account
    Database.Get(testDatabase.getAci()).AccountsTable.add(testDatabase.selfAddress.getNumber().get(), testDatabase.getAci(), IServersTable.DEFAULT_SERVER);
  }

  @AfterEach
  void tearDown() throws IOException {
    Database.close();
    TestUtil.cleanupTestPostgresDatabase(testDatabase);
  }

  @SuppressWarnings("deprecation")
  @Test
  @DisplayName("migrate from JSON store to Database")
  @EnabledIfEnvironmentVariable(named = "SIGNALD_POSTGRES_TEST_DATABASE", matches = ".*")
  void migrateFromJsonToDatabase() throws IOException, SQLException, UnregisteredUserError, InternalError {
    ObjectMapper mapper = JSONUtil.GetMapper();
    String testContactsJson = "  {\"contacts\": ["
                              + "  {"
                              + "    \"name\": \"\u2068John Smith\u2069\","
                              + "    \"address\" : {\"number\": \"+12345678901\", \"uuid\" : \"1acbe9cc-9917-4d2c-a4f2-c9d9389975ff\"},"
                              + "    \"color\" : \"ultramarine\","
                              + "    \"messageExpirationTime\" : 0"
                              + "  },"
                              + "  {"
                              + "    \"name\" : \"\u2068Foo Bar\u2069\","
                              + "    \"address\" : {\"number\" : \"+12342342341\", \"uuid\" : \"78aef659-a594-4c2a-817e-4a7a0f4c87fe\"},"
                              + "    \"color\" : \"ultramarine\","
                              + "    \"profileKey\" : \"pwoheanZQfeOHEA2ly2xHc3123O5C6H9AygoyO+Q85I=\","
                              + "    \"messageExpirationTime\" : 3600,"
                              + "    \"inboxPosition\" : 0"
                              + "  },"
                              + "  {"
                              + "    \"name\" : \"\u2068Another Friend\u2069\","
                              + "    \"address\" : {\"number\" : \"+10987654321\", \"uuid\" : \"507eed40-671f-4150-b554-f001212decc4\"},"
                              + "    \"color\" : \"ultramarine\","
                              + "    \"profileKey\" : \"a3STnOH8AqjsTAIfNvy/2SFPLriAJ0T3LxQmbVU9VhE=\","
                              + "    \"messageExpirationTime\" : 0,"
                              + "    \"inboxPosition\" : 5"
                              + "  }"
                              + "]}";
    var contactStore = mapper.readValue(testContactsJson, LegacyContactStore.class);

    var account = new Account(testDatabase.getAci());
    contactStore.migrateToDB(account);
    var allContacts = Database.Get(account.getACI()).ContactsTable.getAll();

    // First contact
    Assertions.assertEquals("\u2068John Smith\u2069", allContacts.get(0).name);
    Assertions.assertEquals("1acbe9cc-9917-4d2c-a4f2-c9d9389975ff", allContacts.get(0).recipient.getServiceId().toString());
    Assertions.assertEquals("ultramarine", allContacts.get(0).color);
    Assertions.assertEquals(0, allContacts.get(0).messageExpirationTime);

    // Second contact
    Assertions.assertEquals("\u2068Foo Bar\u2069", allContacts.get(1).name);
    Assertions.assertEquals("78aef659-a594-4c2a-817e-4a7a0f4c87fe", allContacts.get(1).recipient.getServiceId().toString());
    Assertions.assertEquals("ultramarine", allContacts.get(1).color);
    Assertions.assertArrayEquals(new byte[] {(byte)0xa7, (byte)0x0a, (byte)0x21, (byte)0x79, (byte)0xa9, (byte)0xd9, (byte)0x41, (byte)0xf7, (byte)0x8e, (byte)0x1c, (byte)0x40,
                                             (byte)0x36, (byte)0x97, (byte)0x2d, (byte)0xb1, (byte)0x1d, (byte)0xcd, (byte)0xf5, (byte)0xdb, (byte)0x73, (byte)0xb9, (byte)0x0b,
                                             (byte)0xa1, (byte)0xfd, (byte)0x03, (byte)0x28, (byte)0x28, (byte)0xc8, (byte)0xef, (byte)0x90, (byte)0xf3, (byte)0x92},
                                 allContacts.get(1).profileKey);
    Assertions.assertEquals(3600, allContacts.get(1).messageExpirationTime);
    Assertions.assertEquals(0, allContacts.get(1).inboxPosition);

    // Third contact
    Assertions.assertEquals("\u2068Another Friend\u2069", allContacts.get(2).name);
    Assertions.assertEquals("507eed40-671f-4150-b554-f001212decc4", allContacts.get(2).recipient.getServiceId().toString());
    Assertions.assertEquals("ultramarine", allContacts.get(2).color);
    Assertions.assertArrayEquals(new byte[] {(byte)0x6b, (byte)0x74, (byte)0x93, (byte)0x9c, (byte)0xe1, (byte)0xfc, (byte)0x02, (byte)0xa8, (byte)0xec, (byte)0x4c, (byte)0x02,
                                             (byte)0x1f, (byte)0x36, (byte)0xfc, (byte)0xbf, (byte)0xd9, (byte)0x21, (byte)0x4f, (byte)0x2e, (byte)0xb8, (byte)0x80, (byte)0x27,
                                             (byte)0x44, (byte)0xf7, (byte)0x2f, (byte)0x14, (byte)0x26, (byte)0x6d, (byte)0x55, (byte)0x3d, (byte)0x56, (byte)0x11},
                                 allContacts.get(2).profileKey);
    Assertions.assertEquals(0, allContacts.get(2).messageExpirationTime);
    Assertions.assertEquals(5, allContacts.get(2).inboxPosition);
  }
}
