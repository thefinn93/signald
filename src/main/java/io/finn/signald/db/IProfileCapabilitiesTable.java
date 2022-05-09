package io.finn.signald.db;

import io.finn.signald.storage.LegacySignalProfile;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

public interface IProfileCapabilitiesTable {
  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String STORAGE = "storage";
  String GV1_MIGRATION = "gv1_migration";
  String SENDER_KEY = "sender_key";
  String ANNOUNCEMENT_GROUP = "announcement_group";
  String CHANGE_NUMBER = "change_number";
  String STORIES = "stories";

  void set(Recipient recipient, Capabilities capabilities) throws SQLException;
  boolean get(Recipient recipient, String capability) throws SQLException;
  Capabilities getAll(Recipient recipient) throws SQLException;

  class Capabilities {
    private boolean storage;
    private boolean gv1Migration;
    private boolean senderKey;
    private boolean announcementGroup;
    private boolean changeNumber;
    private boolean stories;

    public Capabilities() {}

    public Capabilities(SignalServiceProfile.Capabilities c) {
      storage = c.isStorage();
      gv1Migration = c.isGv1Migration();
      senderKey = c.isSenderKey();
      announcementGroup = c.isAnnouncementGroup();
      changeNumber = c.isChangeNumber();
      stories = c.isStories();
    }

    @Deprecated
    public Capabilities(LegacySignalProfile.Capabilities c) {
      storage = c.storage;
      gv1Migration = c.gv1Migration;
      senderKey = c.senderKey;
      announcementGroup = c.announcementGroup;
      changeNumber = c.changeNumber;
      stories = c.stories;
    }

    public boolean isStorage() { return storage; }

    public void setStorage(boolean storage) { this.storage = storage; }

    public boolean isGv1Migration() { return gv1Migration; }

    public void setGv1Migration(boolean gv1Migration) { this.gv1Migration = gv1Migration; }

    public boolean isSenderKey() { return senderKey; }

    public void setSenderKey(boolean senderKey) { this.senderKey = senderKey; }

    public boolean isAnnouncementGroup() { return announcementGroup; }

    public void setAnnouncementGroup(boolean announcementGroup) { this.announcementGroup = announcementGroup; }

    public boolean isChangeNumber() { return changeNumber; }

    public void setChangeNumber(boolean changeNumber) { this.changeNumber = changeNumber; }

    public boolean isStories() { return stories; }

    public void setStories(boolean stories) { this.stories = stories; }
  }
}
