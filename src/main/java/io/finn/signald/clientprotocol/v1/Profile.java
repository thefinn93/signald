/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Account;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.AuthorizationFailedError;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.SQLError;
import io.finn.signald.clientprotocol.v1.exceptions.UnregisteredUserError;
import io.finn.signald.db.*;
import io.finn.signald.util.FileUtil;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

@Doc("Information about a Signal user")
public class Profile {
  private static final Logger logger = LogManager.getLogger();
  @Doc("The user's name from local contact names if available, or if not in contact list their Signal profile name") public String name;
  @Doc("The user's name from local contact names") @JsonProperty("contact_name") public String contactName;
  @Doc("The user's Signal profile name") @JsonProperty("profile_name") public String profileName;
  @Doc("path to avatar on local disk") public String avatar;
  public JsonAddress address;
  public Capabilities capabilities;
  @Doc("color of the chat with this user") public String color;
  @JsonProperty("inbox_position") public Integer inboxPosition;
  @JsonProperty("expiration_time") public int expirationTime;
  public String about;
  public String emoji;

  @Doc("*base64-encoded* mobilecoin address. Note that this is not the traditional MobileCoin address encoding. Clients "
       + "are responsible for converting between MobileCoin's custom base58 on the user-facing side and base64 encoding "
       + "on the signald side. If unset, null or an empty string, will empty the profile payment address")
  @JsonProperty("mobilecoin_address")
  public String mobileCoinAddress;

  @Doc("currently unclear how these work, as they are not available in the production Signal apps") @JsonProperty("visible_badge_ids") public List<String> visibleBadgeIds;

  public Profile(IContactsTable.ContactInfo contact) {
    if (contact == null) {
      return;
    }
    name = contact.name;
    contactName = contact.name;
    color = contact.color;
    inboxPosition = contact.inboxPosition;
    expirationTime = contact.messageExpirationTime;
    address = new JsonAddress(contact.recipient);
  }

  public Profile(Database db, Recipient recipient, IContactsTable.ContactInfo contact) throws SQLError {
    this(contact);
    try {
      IProfilesTable.Profile profile = db.ProfilesTable.get(recipient);
      if (profile != null) {
        profileName = profile.getSerializedFullName();
        IProfileCapabilitiesTable.Capabilities allCapabilities = db.ProfileCapabilitiesTable.getAll(recipient);
        capabilities = allCapabilities == null ? null : new Capabilities(allCapabilities);
        about = profile.getAbout();
        emoji = profile.getEmoji();

        SignalServiceProtos.PaymentAddress paymentAddress = profile.getPaymentAddress();
        if (paymentAddress != null) {
          mobileCoinAddress = Base64.encodeBytes(paymentAddress.getMobileCoinAddress().getAddress().toByteArray());
        }

        visibleBadgeIds = IProfilesTable.StoredBadge.getVisibleIds(profile.getBadges());
      }
    } catch (SQLException e) {
      throw new SQLError(e);
    }
    if (address == null) {
      address = new JsonAddress(recipient);
    } else {
      address.update(recipient.getAddress());
    }

    if (profileName != null && name == null) {
      name = profileName;
    }
  }

  public void populateAvatar(Account account) throws InternalError, UnregisteredUserError, AuthorizationFailedError {
    Recipient recipient = Common.getRecipient(account.getACI(), address);
    avatar = FileUtil.getProfileAvatarPath(recipient);
  }
}
