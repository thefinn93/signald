package io.finn.signald.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.util.JSONUtil;
import io.sentry.Sentry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public interface IProfilesTable {
  String ACCOUNT_UUID = "account_uuid";
  String RECIPIENT = "recipient";
  String LAST_UPDATE = "last_update";
  String GIVEN_NAME = "given_name";
  String FAMILY_NAME = "family_name";
  String ABOUT = "about";
  String EMOJI = "emoji";
  String PAYMENT_ADDRESS = "payment_address";
  String BADGES = "badges";

  String BADGE_SEPARATOR = "|";

  Profile get(Recipient recipient) throws SQLException;
  void setSerializedName(Recipient recipient, String name) throws SQLException;
  void setAbout(Recipient recipient, String about) throws SQLException;
  void setEmoji(Recipient recipient, String emoji) throws SQLException;
  void setPaymentAddress(Recipient recipient, SignalServiceProtos.PaymentAddress paymentAddress) throws SQLException;
  void setBadges(Recipient recipient, List<SignalServiceProfile.Badge> badges) throws SQLException, JsonProcessingException;

  class Profile {
    private final long lastUpdate;
    private final String givenName;
    private final String familyName;
    private final String about;
    private final String emoji;
    private final SignalServiceProtos.PaymentAddress paymentAddress;
    private final List<StoredBadge> badges;

    public Profile(ResultSet rows) throws SQLException {
      lastUpdate = rows.getLong(LAST_UPDATE);
      givenName = getOrEmptyString(rows, GIVEN_NAME);
      familyName = getOrEmptyString(rows, FAMILY_NAME);
      about = getOrEmptyString(rows, ABOUT);
      emoji = getOrEmptyString(rows, EMOJI);

      byte[] paymentAddressBytes = rows.getBytes(PAYMENT_ADDRESS);
      if (paymentAddressBytes != null) {
        SignalServiceProtos.PaymentAddress tmpPaymentAddress = null;
        try {
          tmpPaymentAddress = SignalServiceProtos.PaymentAddress.parseFrom(paymentAddressBytes);
        } catch (InvalidProtocolBufferException e) {
          LogManager.getLogger().error("error parsing stored payment address proto", e);
        }
        paymentAddress = tmpPaymentAddress;
      } else {
        paymentAddress = null;
      }

      String badgesString = rows.getString(BADGES);
      if (badgesString != null) {
        List<StoredBadge> storedBadges = null;
        try {
          storedBadges = StoredBadge.load(badgesString);
        } catch (JsonProcessingException e) {
          LogManager.getLogger().error("error loading badge list from database: ", e);
          Sentry.captureException(e);
        }
        badges = storedBadges;
      } else {
        badges = List.of();
      }
    }

    public long getLastUpdate() { return lastUpdate; }

    public String getSerializedFullName() {
      if (familyName.equals("")) {
        return givenName;
      } else {
        return givenName + "\0" + familyName;
      }
    }

    public String getGivenName() { return givenName; }

    public String getFamilyName() { return familyName; }

    public String getAbout() { return about; }

    public String getEmoji() { return emoji; }

    public SignalServiceProtos.PaymentAddress getPaymentAddress() { return paymentAddress; }

    public List<StoredBadge> getBadges() { return badges; }

    private static String getOrEmptyString(ResultSet rows, String column) throws SQLException {
      String value = rows.getString(column);
      if (value == null) {
        return "";
      }
      return value;
    }
  }

  class StoredBadge {
    private String id;
    private boolean visible;

    private StoredBadge() {}

    public StoredBadge(SignalServiceProfile.Badge badge) {
      id = badge.getId();
      visible = badge.isVisible();
    }

    public static List<StoredBadge> load(String encoded) throws JsonProcessingException {
      return JSONUtil.GetMapper().readValue(encoded, new TypeReference<>() {});
    }

    public static String serialize(List<SignalServiceProfile.Badge> badges) throws JsonProcessingException {
      return JSONUtil.GetWriter().writeValueAsString(badges.stream().map(StoredBadge::new).collect(Collectors.toList()));
    }

    public static List<String> getVisibleIds(List<StoredBadge> all) {
      return all.stream().filter(IProfilesTable.StoredBadge::isVisible).map(IProfilesTable.StoredBadge::getId).collect(Collectors.toList());
    }

    public String getId() { return id; }

    public boolean isVisible() { return visible; }
  }
}
