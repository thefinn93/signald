/*
 * Copyright (C) 2021 Finn Herzfeld
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

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Recipient;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.SignalProfile;
import java.io.File;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.util.Base64;

@Doc("Information about a Signal user")
public class Profile {
  private static final Logger logger = LogManager.getLogger();
  @Doc("The user's name from local contact names if available, or if not in contact list their Signal profile name") public String name;
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

  public Profile(ContactStore.ContactInfo contact) {
    if (contact != null) {
      name = contact.name;
      color = contact.color;
      inboxPosition = contact.inboxPosition;
      expirationTime = contact.messageExpirationTime;
      address = contact.address;
    }
  }

  public Profile(SignalProfile profile, Recipient recipient, ContactStore.ContactInfo contact) {
    this(contact);

    if (profile != null) {
      profileName = profile.getName();
      capabilities = new Capabilities(profile.getCapabilities());
      about = profile.getAbout();
      emoji = profile.getEmoji();

      try {
        SignalServiceProtos.PaymentAddress paymentAddress = profile.getPaymentAddress();
        if (paymentAddress != null) {
          mobileCoinAddress = Base64.encodeBytes(paymentAddress.getMobileCoinAddress().getAddress().toByteArray());
        }
      } catch (IOException e) {
        logger.warn("error decrypting payment profile address: " + e.getMessage());
      }
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

  public void populateAvatar(Manager m) throws InternalError {
    Recipient recipient = Common.getRecipient(m.getRecipientsTable(), address);
    File f = m.getProfileAvatarFile(recipient);
    if (f == null || !f.exists()) {
      return;
    }
    avatar = f.getAbsolutePath();
  }
}
