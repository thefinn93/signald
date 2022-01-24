package io.finn.signald;

import io.finn.signald.db.GroupsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.SenderKeySharedTable;
import io.finn.signald.db.SenderKeysTable;
import io.finn.signald.exceptions.*;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidRegistrationIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.SignalSessionLock;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

public class MessageSender {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;
  private final Recipient self;

  public MessageSender(Account account) throws SQLException, IOException {
    this.account = account;
    self = account.getRecipients().get(account.getACI());
  }

  public List<SendMessageResult> sendGroupMessage(SignalServiceDataMessage.Builder message, GroupIdentifier recipientGroupId, List<Recipient> members)
      throws UnknownGroupException, NoSendPermissionException, SQLException, IOException, InvalidInputException, NoSuchAccountException, ServerNotFoundException,
             InvalidProxyException, InvalidKeyException, InvalidCertificateException, NoSessionException, InvalidRegistrationIdException, TimeoutException, ExecutionException,
             InterruptedException {
    Optional<GroupsTable.Group> groupOptional = account.getGroupsTable().get(recipientGroupId);
    if (!groupOptional.isPresent()) {
      throw new UnknownGroupException();
    }
    GroupsTable.Group group = groupOptional.get();
    if (members == null) {
      members = group.getMembers();
    }

    if (group.getDecryptedGroup().getIsAnnouncementGroup() == EnabledState.ENABLED && !group.isAdmin(self)) {
      logger.warn("refusing to send to an announcement only group that we're not an admin in.");
      throw new NoSendPermissionException();
    }

    DecryptedTimer timer = group.getDecryptedGroup().getDisappearingMessagesTimer();
    if (timer != null && timer.getDuration() != 0) {
      message.withExpiration(timer.getDuration());
    }

    message.asGroupMessage(group.getSignalServiceGroupV2());

    SignalServiceMessageSender messageSender = account.getSignalDependencies().getMessageSender();

    final boolean isRecipientUpdate = false;
    List<Recipient> senderKeyTargets = new LinkedList<>();
    List<Recipient> legacyTargets = new LinkedList<>();

    List<UnidentifiedAccess> access = new LinkedList<>();

    Manager m = Manager.get(account.getACI());

    // check our own profile first
    ProfileAndCredentialEntry selfProfileAndCredentialEntry = m.getRecipientProfileKeyCredential(self);
    if (selfProfileAndCredentialEntry == null || !selfProfileAndCredentialEntry.getProfile().getCapabilities().senderKey) {
      logger.debug("not all linked devices support sender keys, using legacy send");
      legacyTargets.addAll(members);
    } else {
      for (Recipient member : members) {
        if (self.equals(member)) {
          continue;
        }
        ProfileAndCredentialEntry profileAndCredentialEntry;
        try {
          profileAndCredentialEntry = m.getRecipientProfileKeyCredential(member);
        } catch (ExecutionException e) {
          if (e.getCause() instanceof AuthorizationFailedException) {
            logger.debug("AuthorizationFailedException when trying to refresh profile for {}", member.toRedactedString());
          } else {
            logger.error("unexpected error while refreshing recipient profile key", e);
          }
          legacyTargets.add(member);
          continue;
        }

        Optional<UnidentifiedAccessPair> accessPairs = m.getAccessPairFor(member);
        if (profileAndCredentialEntry == null) {
          legacyTargets.add(member);
          logger.debug("cannot send to {} using sender keys: no profile available", member.toRedactedString());
        } else if (!profileAndCredentialEntry.getProfile().getCapabilities().senderKey) {
          legacyTargets.add(member);
          logger.debug("cannot send to {} using sender keys: profile indicates no support for sender key", member.toRedactedString());
        } else if (!accessPairs.isPresent()) {
          legacyTargets.add(member);
          logger.debug("cannot send to {} using sender keys: cannot get unidentified access", member.toRedactedString());
        } else {
          senderKeyTargets.add(member);
          access.add(accessPairs.get().getTargetUnidentifiedAccess().get());
        }
      }
    }

    List<SendMessageResult> results = new ArrayList<>();

    if (senderKeyTargets.size() > 0) {
      DistributionId distributionId = group.getOrCreateDistributionId();
      long keyCreateTime = getCreateTimeForOurKey(distributionId);
      long keyAge = System.currentTimeMillis() - keyCreateTime;

      if (keyCreateTime != -1 && keyAge > TimeUnit.DAYS.toMillis(BuildConfig.SENDER_KEY_MAX_AGE_DAYS)) {
        logger.debug("DistributionId {} was created at {} and is {} ms old (~{} days). Rotating.", distributionId, keyCreateTime, keyAge, TimeUnit.MILLISECONDS.toDays(keyAge));
        rotateOurKey(distributionId);
      }

      List<SignalServiceAddress> recipientAddresses = senderKeyTargets.stream().map(Recipient::getAddress).collect(Collectors.toList());

      logger.debug("sending group message to {} members via a distribution group", recipientAddresses.size());
      try {
        results.addAll(messageSender.sendGroupDataMessage(distributionId, recipientAddresses, access, isRecipientUpdate, ContentHint.DEFAULT, message.build(),
                                                          SignalServiceMessageSender.SenderKeyGroupEvents.EMPTY));
      } catch (UntrustedIdentityException e) {
        account.getProtocolStore().saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
      } catch (NoSessionException ignored) {
        logger.debug("no session, falling back to legacy send");
        legacyTargets.addAll(senderKeyTargets);
      }
    }

    if (legacyTargets.size() > 0) {
      logger.debug("sending group message to {} members without sender keys", legacyTargets.size());
      results.addAll(m.sendMessage(message, legacyTargets));
    }

    for (SendMessageResult r : results) {
      if (r.getIdentityFailure() != null) {
        try {
          Recipient recipient = account.getRecipients().get(r.getAddress());
          account.getProtocolStore().saveIdentity(recipient, r.getIdentityFailure().getIdentityKey(), TrustLevel.UNTRUSTED);
        } catch (SQLException e) {
          logger.error("error storing new identity", e);
        }
      }
    }
    return results;
  }

  private void rotateOurKey(DistributionId distributionId) throws NoSuchAccountException, SQLException, ServerNotFoundException, IOException, InvalidProxyException {
    SessionLock lock = account.getSignalDependencies().getSessionLock();
    SenderKeysTable senderKeysTable = account.getProtocolStore().getSenderKeys();
    SenderKeySharedTable senderKeySharedTable = account.getProtocolStore().getSenderKeyShared();
    try (SignalSessionLock.Lock ignored = lock.acquire()) {
      senderKeysTable.deleteAllFor(account.getACI().toString(), distributionId);
      senderKeySharedTable.deleteAllFor(distributionId);
    }
  }

  private long getCreateTimeForOurKey(DistributionId distributionId) throws SQLException {
    SignalProtocolAddress address = new SignalProtocolAddress(account.getACI().toString(), account.getDeviceId());
    return account.getProtocolStore().getSenderKeys().getCreatedTime(address, distributionId.asUuid());
  }
}
