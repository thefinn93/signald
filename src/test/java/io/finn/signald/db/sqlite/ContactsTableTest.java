package io.finn.signald.db.sqlite;

import io.finn.signald.db.Database;
import io.finn.signald.db.IContactsTable;
import io.finn.signald.db.TestUtil;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class ContactsTableTest {
  private File databaseFile;
  private final ACI aci = ACI.from(UUID.randomUUID());

  @BeforeEach
  void setUp() throws IOException {
    databaseFile = TestUtil.createAndConfigureTestSQLiteDatabase();
  }

  @AfterEach
  void tearDown() {
    Database.close();
    if (!databaseFile.delete()) {
      System.err.println("Test database file couldn't be deleted: " + databaseFile.getAbsolutePath());
    }
  }

  @Test
  @DisplayName("insert/get contacts")
  void insertGetContacts() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(aci).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Ensure that insert (upsert) returns the contact works properly
    var returnedContact = Database.Get(aci).ContactsTable.update(contactInfo);
    Assertions.assertEquals("\u2068John Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("test", returnedContact.color);
    Assertions.assertEquals(3600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(aci).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068John Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("test", retrievedContact.color);
    Assertions.assertEquals(3600, retrievedContact.messageExpirationTime);
  }

  @Test
  @DisplayName("update contacts")
  void updateContacts() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(aci).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Put it in the table.
    Database.Get(aci).ContactsTable.update(contactInfo);

    var newContactInfo = new IContactsTable.ContactInfo(recipient);
    newContactInfo.name = "\u2068Johnny Smith\u2069";
    newContactInfo.color = "blue";
    newContactInfo.inboxPosition = 4;
    newContactInfo.messageExpirationTime = 600;

    // Update it and check that the update gives back the correct values
    var returnedContact = Database.Get(aci).ContactsTable.update(newContactInfo);
    Assertions.assertEquals("\u2068Johnny Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("blue", returnedContact.color);
    Assertions.assertEquals(4, returnedContact.inboxPosition);
    Assertions.assertEquals(600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(aci).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068Johnny Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("blue", retrievedContact.color);
    Assertions.assertEquals(4, retrievedContact.inboxPosition);
    Assertions.assertEquals(600, retrievedContact.messageExpirationTime);
  }

  @Test
  @DisplayName("update part of a contact")
  void updatePartOfContact() throws SQLException, IOException {
    var otherAddress = UUID.randomUUID();
    var recipient = Database.Get(aci).RecipientsTable.get(new SignalServiceAddress(ACI.from(otherAddress), "+11234567890"));
    var contactInfo = new IContactsTable.ContactInfo(recipient);
    contactInfo.name = "\u2068John Smith\u2069";
    contactInfo.color = "test";
    contactInfo.inboxPosition = 3;
    contactInfo.messageExpirationTime = 3600;

    // Put it in the table.
    Database.Get(aci).ContactsTable.update(contactInfo);

    var newContactInfo = new IContactsTable.ContactInfo(recipient);
    newContactInfo.inboxPosition = 4;

    // Update it and check that the update gives back the correct values
    var returnedContact = Database.Get(aci).ContactsTable.update(newContactInfo);
    Assertions.assertEquals("\u2068John Smith\u2069", returnedContact.name);
    Assertions.assertEquals(otherAddress.toString(), returnedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("test", returnedContact.color);
    Assertions.assertEquals(4, returnedContact.inboxPosition);
    Assertions.assertEquals(3600, returnedContact.messageExpirationTime);

    // Ensure that get returns the contact works properly
    var retrievedContact = Database.Get(aci).ContactsTable.get(recipient);
    Assertions.assertEquals("\u2068John Smith\u2069", retrievedContact.name);
    Assertions.assertEquals(otherAddress.toString(), retrievedContact.recipient.getServiceId().toString());
    Assertions.assertEquals("test", retrievedContact.color);
    Assertions.assertEquals(4, retrievedContact.inboxPosition);
    Assertions.assertEquals(3600, retrievedContact.messageExpirationTime);
  }
}
