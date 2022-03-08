/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.Manager;
import io.finn.signald.Util;
import io.finn.signald.db.Database;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.exceptions.InvalidAddressException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.ContactStore;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.*;

public class SendContactsSyncJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final Manager m;

  public SendContactsSyncJob(Manager manager) { m = manager; }
  @Override
  public void run() throws IOException, UntrustedIdentityException, SQLException, InvalidKeyException, InvalidAddressException {
    File contactsFile = Util.createTempFile();
    AccountData accountData = m.getAccountData();
    Account account = m.getAccount();
    var protocolStore = account.getProtocolStore();

    try {
      try (OutputStream fos = new FileOutputStream(contactsFile)) {
        DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
        for (ContactStore.ContactInfo record : accountData.contactStore.getContacts()) {
          VerifiedMessage verifiedMessage = null;
          var identities = protocolStore.getIdentities(Database.Get(record.address.getACI()).RecipientsTable.get(record.address));
          if (identities.size() == 0) {
            continue;
          }
          IIdentityKeysTable.IdentityKeyRow currentIdentity = null;
          for (var id : identities) {
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
                                      m.createContactAvatarAttachment(Database.Get(record.address.getACI()).RecipientsTable.get(record.address)),
                                      Optional.fromNullable(record.color), Optional.fromNullable(verifiedMessage), Optional.fromNullable(profileKey), false, expirationTimer,
                                      Optional.absent(), false));
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
}
