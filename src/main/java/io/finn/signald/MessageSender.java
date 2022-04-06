package io.finn.signald;

import io.finn.signald.db.Database;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.UnknownGroupException;
import io.finn.signald.jobs.BackgroundJobRunnerThread;
import io.finn.signald.jobs.RefreshProfileJob;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.util.SenderKeyUtil;
import io.sentry.Sentry;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.InvalidUnidentifiedAccessHeaderException;

public class MessageSender {
  private static final Logger logger = LogManager.getLogger();
  private final Account account;
  private final Recipient self;

  public MessageSender(Account account) throws SQLException, IOException {
    this.account = account;
    self = Database.Get(account.getACI()).RecipientsTable.get(account.getACI());
  }

  public List<SendMessageResult> sendGroupMessage(SignalServiceDataMessage.Builder message, GroupIdentifier recipientGroupId, List<Recipient> members)
      throws UnknownGroupException, SQLException, IOException, InvalidInputException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException, InvalidKeyException,
             InvalidCertificateException, InvalidRegistrationIdException, TimeoutException, ExecutionException, InterruptedException {
    var groupOptional = Database.Get(account.getACI()).GroupsTable.get(recipientGroupId);
    if (!groupOptional.isPresent()) {
      throw new UnknownGroupException();
    }
    var group = groupOptional.get();
    if (members == null) {
      members = group.getMembers().stream().filter(x -> !self.equals(x)).collect(Collectors.toList());
    }

    DecryptedTimer timer = group.getDecryptedGroup().getDisappearingMessagesTimer();
    if (timer != null && timer.getDuration() != 0) {
      message.withExpiration(timer.getDuration());
    }

    message.asGroupMessage(group.getSignalServiceGroupV2());

    SignalServiceMessageSender messageSender = account.getSignalDependencies().getMessageSender();

    boolean isRecipientUpdate = false;
    List<Recipient> senderKeyTargets = new LinkedList<>();
    List<Recipient> legacyTargets = new LinkedList<>();

    List<UnidentifiedAccess> access = new LinkedList<>();

    Manager m = Manager.get(account.getACI());
    IRecipientsTable recipientsTable = Database.Get(account.getACI()).RecipientsTable;

    // check our own profile first
    ProfileAndCredentialEntry selfProfileAndCredentialEntry = m.getRecipientProfileKeyCredential(self);
    if (selfProfileAndCredentialEntry == null || selfProfileAndCredentialEntry.getProfile() == null || !selfProfileAndCredentialEntry.getProfile().getCapabilities().senderKey) {
      logger.debug("not all linked devices support sender keys, using legacy send");
      RefreshProfileJob.queueIfNeeded(m, selfProfileAndCredentialEntry);
      legacyTargets.addAll(members);
    } else {
      for (Recipient member : members) {
        if (!member.isRegistered()) {
          legacyTargets.add(member);
          logger.debug("refusing to send to {} using sender keys because we think they're no longer registered on Signal", member.toRedactedString());
          continue;
        }
        ProfileAndCredentialEntry profileAndCredentialEntry = m.getAccountData().profileCredentialStore.get(member);
        RefreshProfileJob.queueIfNeeded(m, profileAndCredentialEntry);
        Optional<UnidentifiedAccessPair> accessPairs = m.getAccessPairFor(member);
        if (profileAndCredentialEntry == null) {
          legacyTargets.add(member);
          logger.debug("cannot send to {} using sender keys: no profile available", member.toRedactedString());
        } else if (profileAndCredentialEntry.getProfile() == null) {
          legacyTargets.add(member);
          BackgroundJobRunnerThread.queue(new RefreshProfileJob(m, profileAndCredentialEntry));
          logger.debug("cannot send to {} using sender keys: profile not yet available", member.toRedactedString());
        } else if (!profileAndCredentialEntry.getProfile().getCapabilities().senderKey) {
          legacyTargets.add(member);
          BackgroundJobRunnerThread.queue(new RefreshProfileJob(m, profileAndCredentialEntry));
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

    // disable sender keys for groups of mixed targets until we can figure out how to avoid the duplicate sync messages
    if (senderKeyTargets.size() > 0) {
      DistributionId distributionId = group.getOrCreateDistributionId();
      long keyCreateTime = getCreateTimeForOurKey(distributionId);
      long keyAge = System.currentTimeMillis() - keyCreateTime;

      if (keyCreateTime != -1 && keyAge > TimeUnit.DAYS.toMillis(BuildConfig.SENDER_KEY_MAX_AGE_DAYS)) {
        logger.debug("DistributionId {} was created at {} and is {} ms old (~{} days). Rotating.", distributionId, keyCreateTime, keyAge, TimeUnit.MILLISECONDS.toDays(keyAge));
        SenderKeyUtil.rotateOurKey(account, distributionId);
      }

      List<SignalServiceAddress> recipientAddresses = senderKeyTargets.stream().map(Recipient::getAddress).collect(Collectors.toList());

      logger.debug("sending group message to {} members via a distribution group", recipientAddresses.size());
      try {
        List<SendMessageResult> skdmResults = messageSender.sendGroupDataMessage(distributionId, recipientAddresses, access, isRecipientUpdate, ContentHint.DEFAULT,
                                                                                 message.build(), SenderKeyGroupEventsLogger.INSTANCE);
        Set<ServiceId> networkFailAddressesForRetry = new HashSet<>();
        for (var result : skdmResults) {
          if (result.isSuccess()) {
            if (self.getAddress().equals(result.getAddress())) {
              isRecipientUpdate = true; // prevent duplicate sync messages from being sent
            }
            results.add(result);
          } else if (result.getProofRequiredFailure() != null) {
            // do not retry if reCAPTCHA required
            // note: isNetworkFailure is true if proofRequiredFailure is present, hence this check must come before the
            // isNetworkFailure branch
            results.add(result);
          } else if (result.isNetworkFailure()) {
            // always guaranteed to have an ACI; don't use address for HashSet because of ambiguity with e164 in server
            // responses
            networkFailAddressesForRetry.add(result.getAddress().getServiceId());
          } else if (result.isUnregisteredFailure()) {
            handleUnregisteredFailure(result);
            results.add(result);
          }
        }

        if (!networkFailAddressesForRetry.isEmpty()) {
          // Retry sender key for all network failures. The `sendGroupDataMessage` method creates a fake network failure
          // if an SKDM send failed (creates fake network failures for users that the SKDM succeeded for or
          // didn't need an SKDM). Reasonable to retry with sender key to these network failures, although note that we
          // are unable to distinguish between a genuine network failure or a fake one. We're leaving out unregistered
          // users this time (see to-do message in the handleUnregisteredFailure method for proper handling)
          //
          // Iterate mutably over senderKeyTargets (and accessIterator) so that we can reuse this whole try-catch for
          // the retry and reuse the unidentified access info.
          final var senderKeyTargetIterator = senderKeyTargets.listIterator();
          final var accessIterator = access.listIterator();
          while (senderKeyTargetIterator.hasNext()) {
            var currentTarget = senderKeyTargetIterator.next();
            accessIterator.next();
            if (!networkFailAddressesForRetry.contains(currentTarget.getAddress().getServiceId())) {
              senderKeyTargetIterator.remove();
              accessIterator.remove();
            }
          }
          logger.debug("detected network failures during sender key send; retrying sender key send to {} recipients", senderKeyTargets.size());

          List<SignalServiceAddress> retryRecipientAddresses = senderKeyTargets.stream().map(Recipient::getAddress).collect(Collectors.toList());

          List<SendMessageResult> senderKeyRetryResults = messageSender.sendGroupDataMessage(distributionId, retryRecipientAddresses, access, isRecipientUpdate,
                                                                                             ContentHint.DEFAULT, message.build(), SenderKeyGroupEventsLogger.INSTANCE);

          for (var result : senderKeyRetryResults) {
            if (result.isSuccess()) {
              if (self.getAddress().equals(result.getAddress())) {
                isRecipientUpdate = true; // prevent duplicate sync messages from being sent
              }
              results.add(result);
            } else if (result.getProofRequiredFailure() != null) {
              // do not attempt legacy send if reCAPTCHA required
              // note: isNetworkFailure is true if proofRequiredFailure is present, hence this check must come before the
              // isNetworkFailure branch
              results.add(result);
            } else if (result.isNetworkFailure()) {
              // don't retry with sender key again
              legacyTargets.add(recipientsTable.get(result.getAddress()));
            } else if (result.isUnregisteredFailure()) {
              handleUnregisteredFailure(result);
              results.add(result);
            }
          }
        }
      } catch (UntrustedIdentityException e) {
        account.getProtocolStore().saveIdentity(e.getIdentifier(), e.getIdentityKey(), Config.getNewKeyTrustLevel());
      } catch (NoSessionException ignored) {
        logger.debug("no session, falling back to legacy send");
        SenderKeyUtil.rotateOurKey(account, distributionId);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidKeyException ignored) {
        logger.debug("invalid key. Falling back to legacy sends");
        SenderKeyUtil.rotateOurKey(account, distributionId);
        legacyTargets.addAll(senderKeyTargets);
      } catch (InvalidUnidentifiedAccessHeaderException ignored) {
        logger.debug("Someone had a bad UD header. Falling back to legacy sends.");
        legacyTargets.addAll(senderKeyTargets);
      } catch (NotFoundException ignored) {
        logger.debug("someone was unregistered, falling back to legacy send");
        legacyTargets.addAll(senderKeyTargets);
      } catch (IllegalStateException ignored) {
        logger.debug("illegal state exception while sending with sender keys, falling back to legacy send");
        legacyTargets.addAll(senderKeyTargets);
      }
    }
    // note: senderKeyTargets and access may have been mutated from the original beyond this point

    if (legacyTargets.size() > 0) {
      logger.debug("sending group message to {} members without sender keys", legacyTargets.size());
      List<SignalServiceAddress> recipientAddresses = legacyTargets.stream().map(Recipient::getAddress).collect(Collectors.toList());
      try {
        results.addAll(messageSender.sendDataMessage(recipientAddresses, m.getAccessPairFor(legacyTargets), isRecipientUpdate, ContentHint.DEFAULT, message.build(),
                                                     SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                                                     sendResult -> logger.trace("Partial message send result: {}", sendResult.isSuccess()), () -> false));
      } catch (UntrustedIdentityException e) {
        account.getProtocolStore().handleUntrustedIdentityException(e);
      }
    }

    for (SendMessageResult r : results) {
      if (r.getIdentityFailure() != null) {
        try {
          Recipient recipient = Database.Get(account.getACI()).RecipientsTable.get(r.getAddress());
          account.getProtocolStore().saveIdentity(recipient, r.getIdentityFailure().getIdentityKey(), Config.getNewKeyTrustLevel());
        } catch (SQLException e) {
          logger.error("error storing new identity", e);
          Sentry.captureException(e);
        }
      }
      if (r.isSuccess()) {
        Recipient recipient = recipientsTable.get(r.getAddress());
        if (!recipient.isRegistered()) {
          recipientsTable.setRegistrationStatus(recipient, true);
        }
      }
    }
    return results;
  }

  private void handleUnregisteredFailure(SendMessageResult unregisteredFailureResult) throws SQLException, IOException {
    IRecipientsTable recipientsTable = Database.Get(account.getACI()).RecipientsTable;
    Recipient recipient = recipientsTable.get(unregisteredFailureResult.getAddress());
    if (!recipient.isRegistered()) {
      logger.debug("found an unregistered recipient");
      recipientsTable.setRegistrationStatus(recipient, false);
    }
  }

  private long getCreateTimeForOurKey(DistributionId distributionId) throws SQLException {
    SignalProtocolAddress address = new SignalProtocolAddress(account.getACI().toString(), account.getDeviceId());
    return Database.Get(account.getACI()).SenderKeysTable.getCreatedTime(address, distributionId.asUuid());
  }
}
