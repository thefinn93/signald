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

package io.finn.signald.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.finn.signald.Account;
import io.finn.signald.BuildConfig;
import io.finn.signald.Manager;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.*;
import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.JSONUtil;
import io.prometheus.client.Counter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.util.RandomUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.util.Base64;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class AccountData {
  @JsonProperty("username") String legacyUsername;
  @JsonProperty("password") String legacyPassword;
  public JsonAddress address;
  @JsonProperty("deviceId") Integer legacyDeviceId;
  @JsonProperty("signalingKey") String legacySignalingKey;
  @JsonProperty("preKeyIdOffset") public int legacyPreKeyIdOffset;
  @JsonProperty("nextSignedPreKeyId") public int legacyNextSignedPreKeyId;
  @JsonProperty("backgroundActionsLastRun") public LegacyBackgroundActionsLastRun legacyBackgroundActionsLastRun = new LegacyBackgroundActionsLastRun();
  @JsonProperty("lastAccountRefresh") public int legacyLastAccountRefresh;
  @JsonProperty public String legacyProfileKey;
  @JsonProperty("axolotlStore") public SignalProtocolStore legacyProtocolStore;
  @JsonProperty("recipientStore") public RecipientStore legacyRecipientStore = new RecipientStore();
  @JsonProperty("groupsV2") public LegacyGroupsV2Storage legacyGroupsV2;

  public boolean registered;
  public GroupStore groupStore;
  public ContactStore contactStore;
  public ProfileCredentialStore profileCredentialStore = new ProfileCredentialStore();
  public int version;

  @JsonIgnore private boolean deleted = false;
  @JsonIgnore private Recipient self;

  static final int VERSION_IMPORT_CONTACT_PROFILES = 1;

  private static String dataPath;
  private static final Logger logger = LogManager.getLogger();

  static final Counter savesCount =
      Counter.build().name(BuildConfig.NAME + "_saves_total").help("Total number of times the JSON file was written to the disk").labelNames("account_uuid").register();

  AccountData() {}

  // create a new pending account
  public AccountData(String pendingIdentifier) {
    legacyUsername = pendingIdentifier;
    address = new JsonAddress(pendingIdentifier);
  }

  public static AccountData load(File storageFile) throws IOException, SQLException {
    ObjectMapper mapper = JSONUtil.GetMapper();

    // TODO: Add locking mechanism to prevent two instances of signald from using the same account at the same time.
    AccountData a = mapper.readValue(storageFile, AccountData.class);
    logger.debug("Loaded account data for " + (a.address == null ? "null" : a.address.toRedactedString()));
    a.validate();
    a.update();
    a.initialize();
    return a;
  }

  private void initialize() throws IOException, SQLException {
    if (address != null && address.uuid != null) {
      self = new RecipientsTable(address.getUUID()).get(address.getUUID());
    }
  }

  public static AccountData createLinkedAccount(SignalServiceAccountManager.NewDeviceRegistrationReturn registration, String password, int registrationId, int deviceId,
                                                UUID server) throws InvalidInputException, IOException, SQLException {
    logger.debug("Creating new local account by linking");
    AccountData a = new AccountData();
    a.address = new JsonAddress(registration.getNumber(), registration.getAci());
    a.initialize();

    if (registration.getProfileKey() != null) {
      a.profileCredentialStore.storeProfileKey(a.self, registration.getProfileKey());
    } else {
      a.generateProfileKey();
    }

    a.registered = true;
    a.init();
    a.save();

    AccountsTable.add(registration.getNumber(), registration.getAci(), Manager.getFileName(registration.getNumber()), server);
    Account account = new Account(registration.getAci());
    account.setDeviceId(deviceId);
    account.setPassword(password);
    account.setIdentityKeyPair(registration.getIdentity());
    account.setLocalRegistrationId(registrationId);

    return a;
  }

  @JsonIgnore
  public RecipientsTable getResolver() {
    return new RecipientsTable(getUUID());
  }

  private void update() throws IOException, SQLException {
    if (address == null) {
      address = new JsonAddress(legacyUsername);
    } else if (address.uuid != null && self == null) {
      self = new RecipientsTable(address.getUUID()).get(address.getUUID());
      ProfileAndCredentialEntry profileKeyEntry = profileCredentialStore.get(self.getAddress());
      if (profileKeyEntry != null) {
        if (profileKeyEntry.getServiceAddress().getAci() == null && address.uuid != null) {
          profileKeyEntry.setAddress(self.getAddress());
        }
      }
    }
    if (legacyGroupsV2 == null) {
      legacyGroupsV2 = new LegacyGroupsV2Storage();
    }
    if (contactStore == null) {
      contactStore = new ContactStore();
    }

    for (GroupInfo g : groupStore.getGroups()) {
      try {
        getMigratedGroupId(Base64.encodeBytes(g.groupId)); // Delete v1 groups that have been migrated to a v2 group
      } catch (InvalidInputException e) {
        logger.error("error migrating v1 group to v2", e);
      }
    }

    if (version < VERSION_IMPORT_CONTACT_PROFILES) {
      // migrate profile keys from contacts to profileCredentialStore
      for (ContactStore.ContactInfo c : contactStore.getContacts()) {
        if (c.profileKey == null) {
          continue;
        }
        try {
          ProfileKey p = new ProfileKey(Base64.decode(c.profileKey));
          Recipient recipient = new RecipientsTable(getUUID()).get(c.address);
          profileCredentialStore.storeProfileKey(recipient, p);
        } catch (InvalidInputException e) {
          logger.warn("Invalid profile key while migrating profile keys from contacts", e);
        }
      }

      if (legacyProfileKey != null) {
        try {
          ProfileKey p = new ProfileKey(Base64.decode(legacyProfileKey));
          profileCredentialStore.storeProfileKey(self, p);
        } catch (InvalidInputException e) {
          logger.warn("Invalid profile key while migrating own profile key", e);
        }
      }

      version = VERSION_IMPORT_CONTACT_PROFILES;
      save();
    }
  }

  public void saveIfNeeded() throws IOException {
    if (profileCredentialStore.isUnsaved()) {
      save();
    }
  }

  public void save() throws IOException {
    if (deleted) {
      logger.debug("refusing to save deleted account");
      return;
    }
    validate();

    savesCount.labels(address.uuid == null ? "null" : address.uuid).inc();

    ObjectWriter writer = JSONUtil.GetWriter();
    File dataPathFile = new File(dataPath);
    if (!dataPathFile.exists()) {
      dataPathFile.mkdirs();
    }
    File destination = new File(dataPath + "/.tmp-" + legacyUsername);
    logger.debug("Saving account to disk");
    writer.writeValue(destination, this);
    profileCredentialStore.markSaved();
    destination.renameTo(new File(dataPath + "/" + legacyUsername));
  }

  public void validate() throws InvalidStorageFileException {
    if (!PhoneNumberFormatter.isValidNumber(this.legacyUsername, null)) {
      throw new InvalidStorageFileException("phone number " + this.legacyUsername + " is not valid");
    }
  }

  public void init() throws InvalidInputException, IOException, SQLException {
    if (address == null && legacyUsername != null) {
      address = new JsonAddress(legacyUsername);
    }

    if (address != null && address.number != null && legacyUsername == null) {
      legacyUsername = address.number;
    }

    if (groupStore == null) {
      groupStore = new GroupStore();
    }

    if (legacyGroupsV2 == null) {
      legacyGroupsV2 = new LegacyGroupsV2Storage();
    }

    if (contactStore == null) {
      contactStore = new ContactStore();
    }

    if (address != null && address.uuid != null) {
      if (self == null) {
        self = new RecipientsTable(address.getUUID()).get(address.getUUID());
      }
      ProfileAndCredentialEntry profileKeyEntry = profileCredentialStore.get(self.getAddress());
      if (profileKeyEntry == null) {
        generateProfileKey();
      } else {
        if (profileKeyEntry.getServiceAddress().getAci() == null && address.uuid != null) {
          profileKeyEntry.setAddress(self.getAddress());
        }
      }
    }
  }

  // Generates a profile key if one does not exist
  public void generateProfileKey() throws InvalidInputException {
    if (profileCredentialStore.get(self.getAddress()) == null) {
      byte[] key = new byte[32];
      RandomUtils.getSecureRandom().nextBytes(key);
      profileCredentialStore.storeProfileKey(self, new ProfileKey(key));
    }
  }

  public void markForDeletion() { deleted = true; }

  public boolean isDeleted() { return deleted; }

  public void delete() throws SQLException, IOException {
    PreKeysTable.deleteAccount(getUUID());
    SessionsTable.deleteAccount(getUUID());
    SignedPreKeysTable.deleteAccount(getUUID());
    IdentityKeysTable.deleteAccount(getUUID());
    RecipientsTable.deleteAccount(getUUID());
    GroupsTable.deleteAccount(getUUID());
    GroupCredentialsTable.deleteAccount(getUUID());
    AccountDataTable.deleteAccount(getUUID());
    AccountsTable.deleteAccount(getUUID());
    MessageQueueTable.deleteAccount(legacyUsername);
    try {
      Files.delete(new File(dataPath + "/" + legacyUsername).toPath());
    } catch (NoSuchFileException ignored) {
    }
    try {
      Files.delete(new File(dataPath + "/" + legacyUsername + ".d").toPath());
    } catch (NoSuchFileException ignored) {
    }
  }

  @JsonSetter("groupsV2Supported")
  public void migrateGroupsV2SupportedFlag(boolean flag) {
    // no op
  }

  @JsonIgnore
  public static void setDataPath(String path) {
    dataPath = path + "/data";
  }

  @JsonIgnore
  public byte[] getSelfUnidentifiedAccessKey() {
    return UnidentifiedAccess.deriveAccessKeyFrom(profileCredentialStore.get(self.getAddress()).getProfileKey());
  }

  @JsonIgnore
  public UUID getUUID() {
    if (address == null) {
      return null;
    }
    return address.getUUID();
  }

  public GroupIdentifier getMigratedGroupId(String groupV1Id) throws IOException, InvalidInputException, SQLException {
    GroupIdentifier groupV2Id = new GroupIdentifier(GroupsUtil.getGroupId(GroupsUtil.deriveV2MigrationMasterKey(Base64.decode(groupV1Id))));
    GroupsTable groupsTable = new GroupsTable(ACI.from(getUUID()));
    Optional<GroupsTable.Group> groupOptional = groupsTable.get(groupV2Id);
    if (groupOptional.isPresent()) {
      groupStore.deleteGroup(groupV1Id);
      return groupOptional.get().getId();
    }
    return groupV2Id;
  }

  @JsonIgnore
  public ProfileKey getProfileKey() throws InvalidInputException {
    ProfileAndCredentialEntry entry = profileCredentialStore.get(self.getAddress());
    if (entry == null) {
      generateProfileKey();
      entry = profileCredentialStore.get(self.getAddress());
    }
    return entry.getProfileKey();
  }

  @JsonIgnore
  public void setProfileKey(ProfileKey profileKey) {
    profileCredentialStore.storeProfileKey(self, profileKey);
  }

  // Jackson getters and setters

  // migrate old threadStore which tracked expiration timers, now moved to groups and contacts
  public void setThreadStore(LegacyThreadStore threadStore) {
    logger.info("Migrating thread store");
    for (LegacyThreadInfo t : threadStore.getThreads()) {
      GroupInfo g = groupStore.getGroup(t.id);
      if (g != null) {
        // thread ID matches a known group
        g.messageExpirationTime = t.messageExpirationTime;
        groupStore.updateGroup(g);
      } else {
        // thread ID does not match a known group. Assume it's a PM
        try {
          Recipient recipient = new RecipientsTable(address.getUUID()).get(t.id);
          ContactStore.ContactInfo c = contactStore.getContact(recipient);
          c.messageExpirationTime = t.messageExpirationTime;
          contactStore.updateContact(c);
        } catch (IOException | SQLException e) {
          logger.warn("exception while importing contact: ", e);
        }
      }
    }
  }

  @JsonIgnore
  public Database getDatabase() {
    return new Database(getUUID());
  }

  @JsonIgnore
  public void setUUID(ACI aci) {
    address.uuid = aci.toString();
  }

  public String getLegacyUsername() { return legacyUsername; }

  public boolean migrateToDB(Account account) throws SQLException {
    boolean needsSave = false;

    if (legacyPassword != null) {
      account.setPassword(legacyPassword);
      legacyPassword = null;
      needsSave = true;
      logger.debug("migrated account password to database");
    }

    if (legacyDeviceId != null) {
      account.setDeviceId(legacyDeviceId);
      legacyDeviceId = null;
      needsSave = true;
      logger.debug("migrated local device id to database");
    } else if (account.getDeviceId() < 0) {
      account.setDeviceId(SignalServiceAddress.DEFAULT_DEVICE_ID);
    }

    if (legacyLastAccountRefresh > 0) {
      account.setLastAccountRefresh(legacyLastAccountRefresh);
      legacyLastAccountRefresh = -1;
      needsSave = true;
    }

    if (legacyNextSignedPreKeyId > -1) {
      account.setNextSignedPreKeyId(legacyNextSignedPreKeyId);
      legacyNextSignedPreKeyId = -1;
      needsSave = true;
    }

    if (legacyPreKeyIdOffset > 0) {
      account.setPreKeyIdOffset(legacyPreKeyIdOffset);
      legacyPreKeyIdOffset = -1;
      needsSave = true;
    }
    return needsSave;
  }
}
