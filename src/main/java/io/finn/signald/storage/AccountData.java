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
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.util.AddressUtil;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.JSONUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.util.RandomUtils;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.util.Base64;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class AccountData {
  public String username;
  public String password;
  public JsonAddress address;
  public int deviceId = SignalServiceAddress.DEFAULT_DEVICE_ID;
  public String signalingKey;
  public int preKeyIdOffset;
  public int nextSignedPreKeyId;

  @JsonProperty public String profileKey;

  public boolean registered;

  public SignalProtocolStore axolotlStore;
  public GroupStore groupStore;
  public GroupsV2Storage groupsV2;
  public ContactStore contactStore;
  public RecipientStore recipientStore = new RecipientStore();
  public ProfileCredentialStore profileCredentialStore = new ProfileCredentialStore();
  public BackgroundActionsLastRun backgroundActionsLastRun = new BackgroundActionsLastRun();

  public int lastAccountRefresh;
  public int version;

  static final int VERSION_IMPORT_CONTACT_PROFILES = 1;

  private static String dataPath;
  private static final Logger logger = LogManager.getLogger();

  public static AccountData load(File storageFile) throws IOException {
    logger.debug("Loading account from disk.");
    ObjectMapper mapper = JSONUtil.GetMapper();

    // TODO: Add locking mechanism to prevent two instances of signald from using the same account at the same time.
    AccountData a = mapper.readValue(storageFile, AccountData.class);
    logger.debug("Loaded account data from file.");
    a.validate();
    a.initProtocolStore();
    a.update();
    return a;
  }

  public static AccountData createLinkedAccount(SignalServiceAccountManager.NewDeviceRegistrationReturn registration, String password, int registrationId, String signalingKey)
      throws InvalidInputException, IOException {
    logger.debug("Creating new local account by linking");
    AccountData a = new AccountData();
    a.address = new JsonAddress(registration.getNumber(), registration.getUuid());
    a.password = password;

    if (registration.getProfileKey() != null) {
      a.profileCredentialStore.storeProfileKey(a.address.getSignalServiceAddress(), new ProfileKey(registration.getProfileKey()));
    } else {
      a.generateProfileKey();
    }

    a.deviceId = registration.getDeviceId();
    a.signalingKey = signalingKey;
    a.axolotlStore = new SignalProtocolStore(registration.getIdentity(), registrationId, a.getResolver());
    a.registered = true;
    a.init();
    a.save();
    return a;
  }

  @JsonIgnore
  public AddressResolver getResolver() {
    return new Resolver();
  }

  public void initProtocolStore() {
    axolotlStore.sessionStore.setResolver(getResolver());
    axolotlStore.identityKeyStore.setResolver(getResolver());
  }

  private void update() throws IOException {
    if (address == null) {
      address = new JsonAddress(username);
    }
    if (groupsV2 == null) {
      groupsV2 = new GroupsV2Storage();
    }
    if (contactStore == null) {
      contactStore = new ContactStore();
    }

    for (GroupInfo g : groupStore.getGroups()) {
      getMigratedGroupId(Base64.encodeBytes(g.groupId)); // Delete v1 groups that have been migrated to a v2 group
    }

    ProfileAndCredentialEntry profileKeyEntry = profileCredentialStore.get(address.getSignalServiceAddress());
    if (profileKeyEntry != null) {
      if (!profileKeyEntry.getServiceAddress().getUuid().isPresent() && address.uuid != null) {
        profileKeyEntry.setAddress(address.getSignalServiceAddress());
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
          profileCredentialStore.storeProfileKey(c.address.getSignalServiceAddress(), p);
        } catch (InvalidInputException e) {
          logger.warn("Invalid profile key while migrating profile keys from contacts", e);
        }
      }

      if (profileKey != null) {
        try {
          ProfileKey p = new ProfileKey(Base64.decode(profileKey));
          profileCredentialStore.storeProfileKey(address.getSignalServiceAddress(), p);
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
    validate();

    ObjectWriter writer = JSONUtil.GetWriter();

    File dataPathFile = new File(dataPath);
    if (!dataPathFile.exists()) {
      dataPathFile.mkdirs();
    }
    File destination = new File(dataPath + "/.tmp-" + username);
    logger.debug("Saving account to disk");
    writer.writeValue(destination, this);
    profileCredentialStore.markSaved();
    destination.renameTo(new File(dataPath + "/" + username));
  }

  public void validate() throws InvalidStorageFileException {
    if (!PhoneNumberFormatter.isValidNumber(this.username, null)) {
      throw new InvalidStorageFileException("phone number " + this.username + " is not valid");
    }
  }

  public void init() throws InvalidInputException {
    if (address == null && username != null) {
      address = new JsonAddress(username);
    }

    if (address != null && address.number != null && username == null) {
      username = address.number;
    }

    if (groupStore == null) {
      groupStore = new GroupStore();
    }

    if (groupsV2 == null) {
      groupsV2 = new GroupsV2Storage();
    }

    if (contactStore == null) {
      contactStore = new ContactStore();
    }

    if (address != null) {
      ProfileAndCredentialEntry profileKeyEntry = profileCredentialStore.get(address.getSignalServiceAddress());
      if (profileKeyEntry == null) {
        generateProfileKey();
      } else {
        if (!profileKeyEntry.getServiceAddress().getUuid().isPresent() && address.uuid != null) {
          profileKeyEntry.setAddress(address.getSignalServiceAddress());
        }
      }
    }
  }

  // Generates a profile key if one does not exist
  public void generateProfileKey() throws InvalidInputException {
    if (profileCredentialStore.get(address.getSignalServiceAddress()) == null) {
      byte[] key = new byte[32];
      RandomUtils.getSecureRandom().nextBytes(key);
      profileCredentialStore.storeProfileKey(address.getSignalServiceAddress(), new ProfileKey(key));
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
    return UnidentifiedAccess.deriveAccessKeyFrom(profileCredentialStore.get(address.getSignalServiceAddress()).getProfileKey());
  }

  @JsonIgnore
  public UUID getUUID() {
    if (address == null) {
      return null;
    }
    return address.getUUID();
  }

  @JsonIgnore
  public DynamicCredentialsProvider getCredentialsProvider() {
    return new DynamicCredentialsProvider(getUUID(), username, password, signalingKey, deviceId);
  }

  public String getMigratedGroupId(String groupV1Id) throws IOException {
    String groupV2Id = Base64.encodeBytes(GroupsUtil.getGroupId(GroupsUtil.deriveV2MigrationMasterKey(Base64.decode(groupV1Id))));
    List<Group> v2Groups = groupsV2.groups.stream().filter(g -> g.getID().equals(groupV2Id)).collect(Collectors.toList());
    if (v2Groups.size() > 0) {
      groupStore.deleteGroup(groupV1Id);
      return v2Groups.get(0).getID();
    }
    return groupV1Id;
  }

  @JsonIgnore
  public ProfileKey getProfileKey() throws InvalidInputException {
    ProfileAndCredentialEntry entry = profileCredentialStore.get(address.getSignalServiceAddress());
    if (entry == null) {
      generateProfileKey();
      entry = profileCredentialStore.get(address.getSignalServiceAddress());
    }
    return entry.getProfileKey();
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
        ContactStore.ContactInfo c = contactStore.getContact(t.id);
        c.messageExpirationTime = t.messageExpirationTime;
        contactStore.updateContact(c);
      }
    }
  }

  @JsonIgnore
  public Database getDatabase() throws SQLException {
    return new Database(getUUID());
  }

  public class Resolver implements AddressResolver {

    public SignalServiceAddress resolve(String identifier) {
      SignalServiceAddress address = AddressUtil.fromIdentifier(identifier);
      return resolve(address);
    }

    public SignalServiceAddress resolve(SignalServiceAddress a) {
      if (a.matches(address.getSignalServiceAddress())) {
        return address.getSignalServiceAddress();
      }

      return recipientStore.resolve(a);
    }

    public Collection<SignalServiceAddress> resolve(Collection<SignalServiceAddress> partials) {
      Collection<SignalServiceAddress> full = new ArrayList<>();
      for (SignalServiceAddress p : partials) {
        full.add(resolve(p));
      }
      return full;
    }
  }
}
