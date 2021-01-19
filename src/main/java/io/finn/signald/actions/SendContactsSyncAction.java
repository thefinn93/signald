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

package io.finn.signald.actions;

import io.finn.signald.Manager;
import io.finn.signald.Util;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.IdentityKeyStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

public class SendContactsSyncAction implements Action {
  private static final Logger logger = LogManager.getLogger();

  @Override
  public void run(Manager m) throws IOException, UntrustedIdentityException {
    File contactsFile = Util.createTempFile();
    AccountData accountData = m.getAccountData();

    try {
      try (OutputStream fos = new FileOutputStream(contactsFile)) {
        DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
        for (ContactStore.ContactInfo record : accountData.contactStore.getContacts()) {
          VerifiedMessage verifiedMessage = null;
          List<IdentityKeyStore.Identity> identities = accountData.axolotlStore.identityKeyStore.getIdentities(record.address.getSignalServiceAddress());
          if (identities.size() == 0) {
            continue;
          }
          IdentityKeyStore.Identity currentIdentity = null;
          for (IdentityKeyStore.Identity id : identities) {
            if (currentIdentity == null || id.getDateAdded().after(currentIdentity.getDateAdded())) {
              currentIdentity = id;
            }
          }

          if (currentIdentity != null) {
            verifiedMessage = new VerifiedMessage(record.address.getSignalServiceAddress(), currentIdentity.getKey(), currentIdentity.getTrustLevel().toVerifiedState(),
                                                  currentIdentity.getDateAdded().getTime());
          }

          // TODO: Don't hard code `false` value for blocked argument
          Optional<Integer> expirationTimer = Optional.absent();
          ProfileAndCredentialEntry profileAndCredential = accountData.profileCredentialStore.get(record.address.getSignalServiceAddress());
          ProfileKey profileKey = profileAndCredential == null ? null : profileAndCredential.getProfileKey();
          out.write(new DeviceContact(record.address.getSignalServiceAddress(), Optional.fromNullable(record.name),
                                      m.createContactAvatarAttachment(record.address.getSignalServiceAddress()), Optional.fromNullable(record.color),
                                      Optional.fromNullable(verifiedMessage), Optional.of(profileKey), false, expirationTimer, Optional.absent(), false));
        }
      }

      if (contactsFile.exists() && contactsFile.length() > 0) {
        try (FileInputStream contactsFileStream = new FileInputStream(contactsFile)) {
          SignalServiceAttachmentStream attachmentStream =
              SignalServiceAttachment.newStreamBuilder().withStream(contactsFileStream).withContentType("application/octet-stream").withLength(contactsFile.length()).build();

          m.sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, true)));
        }
      }
    } finally {
      try {
        Files.delete(contactsFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete contacts temp file " + contactsFile + ": " + e.getMessage());
      }
    }
    accountData.save();
  }

  @Override
  public String getName() {
    return SendContactsSyncAction.class.getName();
  }
}
