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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.util.AddressUtil;
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
import org.whispersystems.util.Base64;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

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

  public boolean groupsV2Supported;

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

  public static void createLinkedAccount(SignalServiceAccountManager.NewDeviceRegistrationReturn registration, String password, int registrationId, String signalingKey)
      throws InvalidInputException, IOException {
    logger.debug("Creating new local account by linking");
    AccountData a = new AccountData();
    a.address = new JsonAddress(registration.getNumber(), registration.getUuid());
    a.password = password;

    // if the profileKey returned is null, a new one will be generated when we call a.init()
    if (registration.getProfileKey() != null) {
      a.setProfileKey(registration.getProfileKey());
    }

    a.deviceId = registration.getDeviceId();
    a.signalingKey = signalingKey;
    a.axolotlStore = new SignalProtocolStore(registration.getIdentity(), registrationId, a.getResolver());
    a.registered = true;
    a.init();
    a.save();
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

    if (profileKey == null) {
      generateProfileKey();
    }
  }

  // Generates a profile key if one does not exist
  public void generateProfileKey() throws InvalidInputException {
    if (profileKey == null) {
      byte[] key = new byte[32];
      RandomUtils.getSecureRandom().nextBytes(key);
      setProfileKey(key);
    }
  }

  @JsonIgnore
  public static void setDataPath(String path) {
    dataPath = path + "/data";
  }

  @JsonIgnore
  public byte[] getProfileKeyBytes() {
    try {
      return Base64.decode(profileKey);
    } catch (IOException e) {
      return null;
    }
  }

  @JsonIgnore
  public ProfileKey getProfileKey() throws IOException, InvalidInputException {
    if (profileKey == null || profileKey.equals("")) {
      return null;
    }
    return new ProfileKey(Base64.decode(profileKey));
  }

  @JsonIgnore
  public void setProfileKey(ProfileKey key) {
    if (key == null) {
      profileKey = "";
      return;
    }
    profileKey = Base64.encodeBytes(key.serialize());
  }

  // sets the profile key by bytes, checking for validity first
  @JsonIgnore
  public void setProfileKey(byte[] bytes) throws InvalidInputException {
    setProfileKey(new ProfileKey(bytes));
  }

  @JsonIgnore
  public byte[] getSelfUnidentifiedAccessKey() throws IOException, InvalidInputException {
    return UnidentifiedAccess.deriveAccessKeyFrom(getProfileKey());
  }

  @JsonIgnore
  public UUID getUUID() {
    if (address == null) {
      return null;
    }
    return address.getUUID();
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
