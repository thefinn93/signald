package io.finn.signald.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.UnregisteredUserError;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceContact;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.storage.SignalContactRecord;
import org.whispersystems.util.Base64;

public interface IContactsTable {
  Logger logger = LogManager.getLogger();

  // Column names
  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String NAME = "name";
  String COLOR = "color";
  String PROFILE_KEY = "profile_key";
  String MESSAGE_EXPIRATION_TIME = "message_expiration_time";
  String INBOX_POSITION = "inbox_position";

  ContactInfo get(Recipient recipient) throws SQLException;
  ArrayList<ContactInfo> getAll() throws SQLException;

  ACI getACI();

  void addBatch(List<ContactInfo> contacts) throws SQLException;

  ContactInfo update(Recipient recipient, String name, String color, byte[] profileKey, Integer messageExpirationTime, Integer inboxPosition) throws SQLException;

  default ContactInfo update(JsonContactInfo contactInfo) throws UnregisteredUserError, InternalError, SQLException, IOException {
    return update(new ContactInfo(getACI(), contactInfo));
  }

  default ContactInfo update(DeviceContact c) throws SQLException, IOException {
    Recipient recipient = Database.Get(getACI()).RecipientsTable.get(c.getAddress());
    return update(recipient, c.getName().orElse(null), c.getColor().orElse(null), c.getProfileKey().isPresent() ? c.getProfileKey().get().serialize() : null,
                  c.getExpirationTimer().orElse(null), c.getInboxPosition().orElse(null));
  }

  default ContactInfo update(SignalContactRecord contactRecord) throws SQLException, IOException {
    Recipient recipient = Database.Get(getACI()).RecipientsTable.get(contactRecord.getAddress());
    String name = null;
    if (contactRecord.getGivenName().isPresent() && contactRecord.getFamilyName().isPresent()) {
      name = contactRecord.getGivenName().get() + " " + contactRecord.getFamilyName().get();
    } else if (contactRecord.getGivenName().isPresent() || contactRecord.getFamilyName().isPresent()) {
      name = contactRecord.getGivenName().orElse("") + contactRecord.getFamilyName().orElse("");
    }
    return update(recipient, name, null, contactRecord.getProfileKey().orElse(null), null, null);
  }

  default ContactInfo update(ContactInfo contactInfo) throws SQLException {
    return update(contactInfo.recipient, contactInfo.name, contactInfo.color, contactInfo.profileKey, contactInfo.messageExpirationTime, contactInfo.inboxPosition);
  }

  void clear() throws SQLException;

  class JsonContactInfo {
    @JsonProperty public String name;
    @JsonProperty public JsonAddress address;
    @JsonProperty public String color;
    @JsonProperty public String profileKey;
    @JsonProperty public int messageExpirationTime;
    public Integer inboxPosition;
  }

  class ContactInfo {
    public String name;
    public Recipient recipient;
    public String color;
    public byte[] profileKey;
    public Integer messageExpirationTime;
    public Integer inboxPosition;

    public ContactInfo() {}
    public ContactInfo(Recipient recipient) { this.recipient = recipient; }

    public ContactInfo(ACI aci, JsonContactInfo jsonContactInfo) throws UnregisteredUserError, InternalError, SQLException, IOException {
      this(jsonContactInfo.name, Database.Get(aci).RecipientsTable.get(jsonContactInfo.address), jsonContactInfo.color, new byte[] {}, jsonContactInfo.messageExpirationTime,
           jsonContactInfo.inboxPosition);
      if (jsonContactInfo.profileKey != null) {
        try {
          this.profileKey = Base64.decode(jsonContactInfo.profileKey);
        } catch (IOException e) {
          logger.warn("Failed to decrypt profile key bytes", e);
        }
      }
    }

    public ContactInfo(String name, Recipient recipient, String color, byte[] profileKey, int messageExpirationTime, Integer inboxPosition) {
      this.name = name;
      this.recipient = recipient;
      this.color = color;
      this.profileKey = profileKey;
      this.messageExpirationTime = messageExpirationTime;
      this.inboxPosition = inboxPosition;
    }
  }
}
