/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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
import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.db.Database;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.util.JSONUtil;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.util.Base64;

@Deprecated
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class LegacyAccountData {
  @Deprecated @JsonProperty("username") String legacyUsername;
  @Deprecated @JsonProperty("password") String legacyPassword;
  @Deprecated public JsonAddress address;
  @Deprecated @JsonProperty("deviceId") Integer legacyDeviceId;
  @Deprecated @JsonProperty("signalingKey") String legacySignalingKey;
  @Deprecated @JsonProperty("preKeyIdOffset") public int legacyPreKeyIdOffset;
  @Deprecated @JsonProperty("nextSignedPreKeyId") public int legacyNextSignedPreKeyId;
  @Deprecated @JsonProperty("backgroundActionsLastRun") public LegacyBackgroundActionsLastRun legacyBackgroundActionsLastRun = new LegacyBackgroundActionsLastRun();
  @Deprecated @JsonProperty("lastAccountRefresh") public int legacyLastAccountRefresh;
  @Deprecated @JsonProperty public String legacyProfileKey;
  @Deprecated @JsonProperty("axolotlStore") public LegacySignalProtocolStore legacyProtocolStore;
  @Deprecated @JsonProperty("recipientStore") public LegacyRecipientStore legacyRecipientStore = new LegacyRecipientStore();
  @Deprecated @JsonProperty("groupsV2") public LegacyGroupsV2Storage legacyGroupsV2;
  @Deprecated @JsonProperty("contactStore") public LegacyContactStore legacyContactStore;
  @Deprecated @JsonProperty("profileCredentialStore") public LegacyProfileCredentialStore legacyProfileCredentialStore = new LegacyProfileCredentialStore();

  @Deprecated public boolean registered;
  @Deprecated @JsonProperty("groupStore") public LegacyGroupStore legacyGroupStore;
  @Deprecated public int version;

  @Deprecated @JsonIgnore private Recipient self;

  static final int VERSION_IMPORT_CONTACT_PROFILES = 1;
  public static final int DELETED_DO_NOT_SAVE = 2; // Indicates this account has been fully migrated out of the legacy data store

  private static String dataPath;
  private static final Logger logger = LogManager.getLogger();

  static final Counter savesCount =
      Counter.build().name(BuildConfig.NAME + "_saves_total").help("Total number of times the JSON file was written to the disk").labelNames("account_uuid").register();
  private static final Histogram saveTime =
      Histogram.build().name(BuildConfig.NAME + "_save_time_seconds").help("json file write time in seconds.").labelNames("account_uuid").register();

  LegacyAccountData() {}

  public static LegacyAccountData load(File storageFile) throws IOException, SQLException {
    ObjectMapper mapper = JSONUtil.GetMapper();

    // TODO: Add locking mechanism to prevent two instances of signald from using the same account at the same time.
    LegacyAccountData a = mapper.readValue(storageFile, LegacyAccountData.class);
    logger.debug("Loaded account data for " + (a.address == null ? "null" : a.address.toRedactedString()));
    a.validate();
    a.update();
    a.initialize();
    return a;
  }

  private void initialize() throws IOException, SQLException {
    if (address != null && address.uuid != null) {
      self = Database.Get(address.getACI()).RecipientsTable.get(address.getServiceID());
      if (legacyProfileCredentialStore != null) {
        legacyProfileCredentialStore.initialize(self);
      }
    }
  }

  private void update() throws IOException, SQLException {
    if (address == null) {
      address = new JsonAddress(legacyUsername);
    } else if (address.uuid != null && self == null) {
      self = Database.Get(address.getACI()).RecipientsTable.get(address.getUUID());
      if (legacyProfileCredentialStore != null) {
        legacyProfileCredentialStore.initialize(self);
      }
    }
    if (legacyGroupsV2 == null) {
      legacyGroupsV2 = new LegacyGroupsV2Storage();
    }
    if (legacyContactStore == null) {
      legacyContactStore = new LegacyContactStore();
    }

    if (version < VERSION_IMPORT_CONTACT_PROFILES) {
      // migrate profile keys from contacts to profileCredentialStore
      for (var c : Database.Get(address.getACI()).ContactsTable.getAll()) {
        if (c.profileKey == null || c.profileKey.length == 0) {
          continue;
        }
        try {
          ProfileKey p = new ProfileKey(c.profileKey);
          Recipient recipient = Database.Get(ACI.from(self.getServiceId().uuid())).RecipientsTable.get(c.recipient.getServiceId());
          legacyProfileCredentialStore.storeProfileKey(recipient, p);
        } catch (InvalidInputException e) {
          logger.warn("Invalid profile key while migrating profile keys from contacts", e);
        }
      }

      if (legacyProfileKey != null) {
        try {
          ProfileKey p = new ProfileKey(Base64.decode(legacyProfileKey));
          legacyProfileCredentialStore.storeProfileKey(self, p);
        } catch (InvalidInputException e) {
          logger.warn("Invalid profile key while migrating own profile key", e);
        }
      }

      version = VERSION_IMPORT_CONTACT_PROFILES;
      save();
    }
  }

  @Deprecated
  public void save() throws IOException {
    if (version >= DELETED_DO_NOT_SAVE) {
      return;
    }

    validate();

    savesCount.labels(address.uuid == null ? "" : address.uuid).inc();
    Histogram.Timer timer = saveTime.labels(address.uuid == null ? "" : address.uuid).startTimer();

    try {
      ObjectWriter writer = JSONUtil.GetWriter();
      File dataPathFile = new File(dataPath);
      if (!dataPathFile.exists()) {
        dataPathFile.mkdirs();
      }
      File destination = new File(dataPath + "/.tmp-" + legacyUsername);
      logger.debug("Saving account to disk");
      writer.writeValue(destination, this);
      destination.renameTo(new File(dataPath + "/" + legacyUsername));
    } finally {
      timer.observeDuration();
    }
  }

  public void validate() throws InvalidStorageFileException {
    if (!PhoneNumberFormatter.isValidNumber(this.legacyUsername, null)) {
      throw new InvalidStorageFileException("phone number " + this.legacyUsername + " is not valid");
    }
  }

  public void delete() throws SQLException, IOException {
    try {
      Files.delete(new File(dataPath + "/" + legacyUsername).toPath());
    } catch (NoSuchFileException ignored) {
    }
    try {
      Files.delete(new File(dataPath + "/" + legacyUsername + ".d").toPath());
    } catch (NoSuchFileException ignored) {
    }
  }

  @JsonIgnore
  public static void setDataPath(String path) {
    dataPath = path;
  }

  @JsonIgnore
  public ACI getACI() {
    if (address == null) {
      return null;
    }
    return ACI.parseOrThrow(address.uuid);
  }

  // Jackson getters and setters

  @JsonSetter("groupsV2Supported")
  public void migrateGroupsV2SupportedFlag(boolean flag) {
    // no op
  }

  // migrate old threadStore which tracked expiration timers, now moved to groups and contacts
  public void setThreadStore(LegacyThreadStore threadStore) {
    logger.info("Migrating thread store");
    for (LegacyThreadInfo t : threadStore.getThreads()) {
      LegacyGroupInfo g = legacyGroupStore.getGroup(t.id);
      if (g != null) {
        // thread ID matches a known group
        g.messageExpirationTime = t.messageExpirationTime;
        legacyGroupStore.updateGroup(g);
      } else {
        // thread ID does not match a known group. Assume it's a PM
        try {
          Recipient recipient = Database.Get(address.getACI()).RecipientsTable.get(t.id);
          Database.Get(address.getACI()).ContactsTable.update(recipient, null, null, null, t.messageExpirationTime, null);
        } catch (IOException | SQLException e) {
          logger.warn("exception while importing contact: ", e);
        }
      }
    }
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
