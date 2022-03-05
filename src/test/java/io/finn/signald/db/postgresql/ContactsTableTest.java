package io.finn.signald.db.postgresql;

import io.finn.signald.db.Database;
import io.finn.signald.db.IContactsTable;
import io.finn.signald.db.IServersTable;
import io.finn.signald.db.TestUtil;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ContactsTableTest {
  private TestUtil.TestPostgresDatabase testDatabase;

  @BeforeEach
  void setUp() throws IOException, SQLException {
    testDatabase = TestUtil.createAndConfigureTestPostgresDatabase();

    // Create the account
    Database.Get(testDatabase.getAci()).AccountsTable.add(testDatabase.selfAddress.getNumber().get(), testDatabase.getAci(), "/not/here", IServersTable.DEFAULT_SERVER);
  }

  @AfterEach
  void tearDown() throws IOException {
    Database.close();
    TestUtil.cleanupTestPostgresDatabase(testDatabase);
  }

  @Test
  @DisplayName("insert/get contacts")
  @EnabledIfEnvironmentVariable(named = "SIGNALD_POSTGRES_TEST_DATABASE", matches = ".*")
  void insertGetContacts() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(testDatabase.getAci()).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Ensure that insert (upsert) returns the contact works properly
    var returnedContact = Database.Get(testDatabase.getAci()).ContactsTable.update(contactInfo);
    Assertions.assertEquals("\u2068John Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getACI().toString());
    Assertions.assertEquals("test", returnedContact.color);
    Assertions.assertEquals(3, returnedContact.inboxPosition);
    Assertions.assertEquals(3600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(testDatabase.getAci()).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068John Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getACI().toString());
    Assertions.assertEquals("test", retrievedContact.color);
    Assertions.assertEquals(3, returnedContact.inboxPosition);
    Assertions.assertEquals(3600, retrievedContact.messageExpirationTime);
  }

  @Test
  @DisplayName("update contacts")
  @EnabledIfEnvironmentVariable(named = "SIGNALD_POSTGRES_TEST_DATABASE", matches = ".*")
  void updateContacts() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(testDatabase.getAci()).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Put it in the table.
    Database.Get(testDatabase.getAci()).ContactsTable.update(contactInfo);

    var newContactInfo = new IContactsTable.ContactInfo(recipient);
    newContactInfo.name = "\u2068Johnny Smith\u2069";
    newContactInfo.color = "blue";
    newContactInfo.inboxPosition = 4;
    newContactInfo.messageExpirationTime = 600;

    // Update it and check that the update gives back the correct values
    var returnedContact = Database.Get(testDatabase.getAci()).ContactsTable.update(newContactInfo);
    Assertions.assertEquals("\u2068Johnny Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getACI().toString());
    Assertions.assertEquals("blue", returnedContact.color);
    Assertions.assertEquals(4, returnedContact.inboxPosition);
    Assertions.assertEquals(600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(testDatabase.getAci()).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068Johnny Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getACI().toString());
    Assertions.assertEquals("blue", retrievedContact.color);
    Assertions.assertEquals(4, retrievedContact.inboxPosition);
    Assertions.assertEquals(600, retrievedContact.messageExpirationTime);
  }

  @Test
  @DisplayName("update part of a contact")
  @EnabledIfEnvironmentVariable(named = "SIGNALD_POSTGRES_TEST_DATABASE", matches = ".*")
  void updatePartOfContact() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(testDatabase.getAci()).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Put it in the table.
    Database.Get(testDatabase.getAci()).ContactsTable.update(contactInfo);

    var newContactInfo = new IContactsTable.ContactInfo(recipient);
    newContactInfo.inboxPosition = 4;

    // Update it and check that the update gives back the correct values
    var returnedContact = Database.Get(testDatabase.getAci()).ContactsTable.update(newContactInfo);
    Assertions.assertEquals("\u2068John Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getACI().toString());
    Assertions.assertEquals("test", returnedContact.color);
    Assertions.assertEquals(4, returnedContact.inboxPosition);
    Assertions.assertEquals(3600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(testDatabase.getAci()).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068John Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getACI().toString());
    Assertions.assertEquals("test", retrievedContact.color);
    Assertions.assertEquals(4, retrievedContact.inboxPosition);
    Assertions.assertEquals(3600, retrievedContact.messageExpirationTime);
  }
}
