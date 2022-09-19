/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.jobs;

import io.finn.signald.Account;
import io.finn.signald.MessageSender;
import io.finn.signald.db.Database;
import io.finn.signald.db.IIdentityKeysTable;
import io.finn.signald.exceptions.InvalidAddressException;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.FileUtil;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.*;

public class SendContactsSyncJob implements Job {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;

  public SendContactsSyncJob(Account account) { this.account = account; }

  @Override
  public void run() throws IOException, UntrustedIdentityException, SQLException, InvalidKeyException, InvalidAddressException, NoSuchAccountException, ServerNotFoundException,
                           InvalidProxyException {
    File contactsFile = FileUtil.createTempFile();
    var protocolStore = account.getProtocolStore();

    try {
      try (OutputStream fos = new FileOutputStream(contactsFile)) {
        DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
        for (var record : Database.Get(account.getACI()).ContactsTable.getAll()) {
          VerifiedMessage verifiedMessage = null;
          var identities = protocolStore.getIdentities(record.recipient);
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
            verifiedMessage = new VerifiedMessage(record.recipient.getAddress(), currentIdentity.getKey(), currentIdentity.getTrustLevel().toVerifiedState(),
                                                  currentIdentity.getDateAdded().getTime());
          }

          // TODO: Don't hard code `false` value for blocked argument
          Optional<Integer> expirationTimer = Optional.empty();
          ProfileKey profileKey = account.getDB().ProfileKeysTable.getProfileKey(record.recipient);
          Optional<SignalServiceAttachmentStream> avatar = FileUtil.createContactAvatarAttachment(record.recipient);
          out.write(new DeviceContact(record.recipient.getAddress(), Optional.ofNullable(record.name), avatar, Optional.ofNullable(record.color),
                                      Optional.ofNullable(verifiedMessage), Optional.ofNullable(profileKey), false, expirationTimer, Optional.empty(), false));
        }
      }

      if (contactsFile.exists() && contactsFile.length() > 0) {
        try (FileInputStream contactsFileStream = new FileInputStream(contactsFile)) {
          SignalServiceAttachmentStream attachmentStream =
              SignalServiceAttachment.newStreamBuilder().withStream(contactsFileStream).withContentType("application/octet-stream").withLength(contactsFile.length()).build();
          new MessageSender(account).sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, true)));
        }
      }
    } finally {
      try {
        Files.delete(contactsFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete contacts temp file " + contactsFile + ": " + e.getMessage());
      }
    }
  }
}
