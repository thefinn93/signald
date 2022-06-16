/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */
package io.finn.signald;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.db.*;
import io.finn.signald.exceptions.*;
import io.finn.signald.jobs.*;
import io.finn.signald.util.MutableLong;
import io.finn.signald.util.SafetyNumberHelper;
import io.finn.signald.util.UnidentifiedAccessUtil;
import io.prometheus.client.Histogram;
import io.sentry.Sentry;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.metadata.*;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.protocol.*;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPrivateKey;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.fingerprint.Fingerprint;
import org.signal.libsignal.protocol.fingerprint.FingerprintParsingException;
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException;
import org.signal.libsignal.protocol.message.DecryptionErrorMessage;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.Medium;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.*;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.util.Base64;

public class Manager {
  private final Logger logger;
  private final SignalServiceConfiguration serviceConfiguration;
  private final ECPublicKey unidentifiedSenderTrustRoot;

  private static final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();
  private static final Histogram messageDecryptionTime =
      Histogram.build().name(BuildConfig.NAME + "_message_decryption_time").help("Time (in seconds) to decrypt incoming messages").labelNames("account_uuid").register();

  private static String dataPath;
  private static String attachmentsPath;
  private static String avatarsPath;
  private static String stickersPath;

  private final ACI aci;
  private final Account account;
  private final Recipient self;
  private final SignalDependencies dependencies;
  public static Manager get(UUID uuid) throws SQLException, NoSuchAccountException, IOException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    return get(ACI.from(uuid));
  }

  public static Manager get(ACI aci) throws SQLException, NoSuchAccountException, IOException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    return get(aci, false);
  }
  public static Manager get(ACI aci, boolean offline)
      throws SQLException, NoSuchAccountException, IOException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m;
    synchronized (managers) {
      if (managers.containsKey(aci.toString())) {
        return managers.get(aci.toString());
      }
      m = new Manager(aci);
      managers.put(aci.toString(), m);
    }

    if (!offline) {
      RefreshPreKeysJob.runIfNeeded(aci, m);
      Account account = new Account(aci);
      account.refreshIfNeeded();
      RefreshProfileJob.queueIfNeeded(account, account.getSelf());
    }
    return m;
  }

  public static Manager get(String e164) throws NoSuchAccountException, SQLException, InvalidProxyException, ServerNotFoundException, InvalidKeyException, IOException {
    UUID uuid = Database.Get().AccountsTable.getUUID(e164);
    return Manager.get(uuid);
  }

  public static List<Manager> getAll() {
    Logger logger = LogManager.getLogger("manager");
    // We have to create a manager for each account that we're listing, which is all of them :/
    List<Manager> allManagers = new LinkedList<>();
    File[] allAccounts = new File(dataPath).listFiles();
    if (allAccounts == null) {
      return allManagers;
    }
    for (File account : allAccounts) {
      if (!account.isDirectory()) {
        try {
          allManagers.add(Manager.get(account.getName()));
        } catch (IOException | NoSuchAccountException | SQLException | InvalidKeyException | ServerNotFoundException | InvalidProxyException e) {
          logger.warn("Failed to load account from " + account.getAbsolutePath(), e);
        }
      }
    }
    return allManagers;
  }

  Manager(ACI aci) throws IOException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, NoSuchAccountException {
    this.aci = aci;
    account = new Account(aci);
    self = account.getSelf();
    logger = LogManager.getLogger("manager-" + Util.redact(aci.toString()));
    var server = Database.Get().AccountsTable.getServer(aci);
    serviceConfiguration = server.getSignalServiceConfiguration();
    unidentifiedSenderTrustRoot = server.getUnidentifiedSenderRoot();
    dependencies = account.getSignalDependencies();
    logger.info("Created a manager for " + Util.redact(aci.toString()));
    synchronized (managers) { managers.put(aci.toString(), this); }
  }

  public static void setDataPath() {
    LogManager.getLogger().debug("Using data folder {}", Config.getDataPath());
    dataPath = Config.getDataPath() + "/data";
    attachmentsPath = Config.getDataPath() + "/attachments";
    avatarsPath = Config.getDataPath() + "/avatars";
    stickersPath = Config.getDataPath() + "/stickers";
  }

  public Account getAccount() { return account; }

  public UUID getUUID() { return aci.uuid(); }

  public ACI getACI() { return aci; }

  public Recipient getOwnRecipient() { return self; }

  public IdentityKey getIdentity() { return account.getProtocolStore().getIdentityKeyPair().getPublicKey(); }

  private String getMessageCachePath() throws NoSuchAccountException, SQLException { return dataPath + "/" + account.getE164() + ".d/msg-cache"; }

  public static void createPrivateDirectories(String path) throws IOException {
    final Path file = new File(path).toPath();
    try {
      Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_WRITE, GROUP_EXECUTE);
      Files.createDirectories(file, PosixFilePermissions.asFileAttribute(perms));
    } catch (UnsupportedOperationException e) {
      Files.createDirectories(file);
    }
  }

  public SignalServiceAccountManager getAccountManager() { return dependencies.getAccountManager(); }

  public static Map<String, String> getQueryMap(String query) {
    String[] params = query.split("&");
    Map<String, String> map = new HashMap<>();
    for (String param : params) {
      String name = URLDecoder.decode(param.split("=")[0], StandardCharsets.UTF_8);
      String value = URLDecoder.decode(param.split("=")[1], StandardCharsets.UTF_8);
      map.put(name, value);
    }
    return map;
  }

  public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException, InvalidInputException, SQLException {
    Map<String, String> query = getQueryMap(linkUri.getRawQuery());
    String deviceIdentifier = query.get("uuid");
    String publicKeyEncoded = query.get("pub_key");

    if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
      throw new RuntimeException("Invalid device link uri");
    }

    ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

    SignalServiceAccountManager accountManager = dependencies.getAccountManager();
    String verificationCode = accountManager.getNewDeviceVerificationCode();
    ProfileKey profileKey = account.getDB().ProfileKeysTable.getProfileKey(account.getSelf());
    accountManager.addDevice(deviceIdentifier, deviceKey, account.getACIIdentityKeyPair(), account.getPNIIdentityKeyPair(), profileKey, verificationCode);
  }

  private List<PreKeyRecord> generatePreKeys() throws SQLException {
    List<PreKeyRecord> records = new LinkedList<>();

    DatabaseAccountDataStore protocolStore = account.getProtocolStore();
    for (int i = 0; i < ServiceConfig.PREKEY_BATCH_SIZE; i++) {
      int preKeyId = (account.getPreKeyIdOffset() + i) % Medium.MAX_VALUE;
      ECKeyPair keyPair = Curve.generateKeyPair();
      PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

      protocolStore.storePreKey(preKeyId, record);
      records.add(record);
    }

    account.setPreKeyIdOffset((account.getPreKeyIdOffset() + ServiceConfig.PREKEY_BATCH_SIZE + 1) % Medium.MAX_VALUE);

    return records;
  }

  private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKey) throws SQLException, InvalidKeyException {
    ECKeyPair keyPair = Curve.generateKeyPair();
    byte[] signature = Curve.calculateSignature(identityKey.getPrivateKey(), keyPair.getPublicKey().serialize());
    int signedPreKeyId = account.getNextSignedPreKeyId();
    SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
    account.getProtocolStore().storeSignedPreKey(signedPreKeyId, record);
    account.setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);
    return record;
  }

  public void refreshPreKeys() throws IOException, SQLException, InvalidKeyException {
    refreshPreKeys(ServiceIdType.ACI);
    refreshPreKeys(ServiceIdType.PNI);
  }

  public void refreshPreKeys(ServiceIdType serviceIdType) throws IOException, SQLException, InvalidKeyException {
    if (serviceIdType != ServiceIdType.ACI) {
      // TODO implement
      return;
    }
    List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
    SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(account.getACIIdentityKeyPair());
    IdentityKeyPair identityKeyPair = account.getACIIdentityKeyPair();
    dependencies.getAccountManager().setPreKeys(serviceIdType, identityKeyPair.getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
  }

  public List<JsonGroupV2Info> getGroupsV2Info() throws SQLException {
    List<JsonGroupV2Info> groups = new ArrayList<>();
    for (var g : Database.Get(account.getACI()).GroupsTable.getAll()) {
      groups.add(g.getJsonGroupV2Info());
    }
    return groups;
  }

  public List<SendMessageResult> sendGroupV2Message(SignalServiceDataMessage.Builder message, IGroupsTable.IGroup group) throws IOException, SQLException {

    DecryptedTimer timer = group.getDecryptedGroup().getDisappearingMessagesTimer();

    if (timer != null && timer.getDuration() != 0) {
      message.withExpiration(timer.getDuration());
    }

    return sendGroupV2Message(message, group.getSignalServiceGroupV2(), group.getMembers());
  }

  public List<SendMessageResult> sendGroupV2Message(SignalServiceDataMessage.Builder message, SignalServiceGroupV2 group, List<Recipient> recipients)
      throws IOException, SQLException {
    message.asGroupMessage(group);
    final List<Recipient> membersSend = new ArrayList<>();
    for (Recipient member : recipients) {
      if (!member.equals(self)) {
        membersSend.add(member);
      }
    }

    return sendMessage(message, membersSend);
  }

  // set expiration for a 1-to-1 conversation
  public List<SendMessageResult> setExpiration(Recipient recipient, int expiresInSeconds) throws IOException, SQLException {
    Database.Get(aci).ContactsTable.update(recipient, null, null, null, expiresInSeconds, null);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate().withExpiration(expiresInSeconds);
    var recipients = new ArrayList<Recipient>(1);
    recipients.add(recipient);
    return sendMessage(messageBuilder, recipients);
  }

  public void requestSyncGroups() throws IOException, SQLException, UntrustedIdentityException {
    SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
    SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
    sendSyncMessage(message);
  }

  public void requestSyncContacts() throws IOException, SQLException, UntrustedIdentityException {
    SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
    SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
    sendSyncMessage(message);
  }

  public void requestSyncConfiguration() throws IOException, SQLException, UntrustedIdentityException {
    SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION).build();
    SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
    sendSyncMessage(message);
  }

  public void sendSyncMessage(SignalServiceSyncMessage message) throws IOException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException, SQLException {
    SignalServiceMessageSender messageSender = dependencies.getMessageSender();
    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      messageSender.sendSyncMessage(message, getAccessPairFor(self));
    } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
      account.getProtocolStore().handleUntrustedIdentityException(e);
      throw e;
    }
  }

  public SendMessageResult sendTypingMessage(SignalServiceTypingMessage message, Recipient recipient) throws IOException {
    SignalServiceMessageSender messageSender = dependencies.getMessageSender();
    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      messageSender.sendTyping(List.of(recipient.getAddress()), getAccessPairFor(List.of(recipient)), message, null);
      return null;
    }
  }

  public SendMessageResult sendReceipt(SignalServiceReceiptMessage message, Recipient recipient) throws IOException, SQLException {
    SignalServiceMessageSender messageSender = dependencies.getMessageSender();
    SignalServiceAddress address = recipient.getAddress();
    try {
      try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
        messageSender.sendReceipt(address, getAccessPairFor(recipient), message);
      }
      if (message.getType() == SignalServiceReceiptMessage.Type.READ) {
        List<ReadMessage> readMessages = new LinkedList<>();
        for (Long ts : message.getTimestamps()) {
          readMessages.add(new ReadMessage(address, ts));
        }
        try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
          messageSender.sendSyncMessage(SignalServiceSyncMessage.forRead(readMessages), getAccessPairFor(self));
        }
      }
      return null;
    } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
      account.getProtocolStore().handleUntrustedIdentityException(e);
      return SendMessageResult.identityFailure(address, e.getIdentityKey());
    }
  }

  public List<SendMessageResult> sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<Recipient> recipients) throws IOException, SQLException {

    ProfileKey profileKey = account.getDB().ProfileKeysTable.getProfileKey(self);
    if (profileKey != null) {
      messageBuilder.withProfileKey(profileKey.serialize());
    }

    SignalServiceDataMessage message = null;

    try {
      SignalServiceMessageSender messageSender = dependencies.getMessageSender();
      message = messageBuilder.build();
      if (message.getGroupContext().isPresent()) {
        try {
          final boolean isRecipientUpdate = false;
          List<SignalServiceAddress> recipientAddresses = recipients.stream().map(Recipient::getAddress).collect(Collectors.toList());
          List<SendMessageResult> result;
          result = messageSender.sendDataMessage(recipientAddresses, getAccessPairFor(recipients), isRecipientUpdate, ContentHint.DEFAULT, message,
                                                 SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                                                 sendResult -> logger.trace("Partial message send result: {}", sendResult.isSuccess()), () -> false);
          for (SendMessageResult r : result) {
            if (r.getIdentityFailure() != null) {
              try {
                Recipient recipient = Database.Get(aci).RecipientsTable.get(r.getAddress());
                account.getProtocolStore().saveIdentity(recipient, r.getIdentityFailure().getIdentityKey(), Config.getNewKeyTrustLevel());
              } catch (SQLException e) {
                logger.error("error storing new identity", e);
                Sentry.captureException(e);
              }
            }
          }
          return result;
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
          account.getProtocolStore().handleUntrustedIdentityException(e);
          return Collections.emptyList();
        }
      } else if (recipients.size() == 1 && recipients.contains(self)) {
        final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessPairFor(self);
        SentTranscriptMessage transcript =
            new SentTranscriptMessage(Optional.of(self.getAddress()), message.getTimestamp(), Optional.of(message), message.getExpiresInSeconds(),
                                      Collections.singletonMap(self.getAddress(), unidentifiedAccess.isPresent()), false, Optional.empty(), Set.of());
        SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

        List<SendMessageResult> results = new ArrayList<>(recipients.size());
        try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
          messageSender.sendSyncMessage(syncMessage, unidentifiedAccess);
        } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
          account.getProtocolStore().handleUntrustedIdentityException(e);
          results.add(SendMessageResult.identityFailure(self.getAddress(), e.getIdentityKey()));
        }
        return results;
      } else {
        // Send to all individually, so sync messages are sent correctly
        List<SendMessageResult> results = new ArrayList<>(recipients.size());
        for (Recipient recipient : recipients) {
          var contact = Database.Get(aci).ContactsTable.get(recipient);
          messageBuilder.withExpiration(contact != null ? contact.messageExpirationTime : 0);
          message = messageBuilder.build();
          try {
            if (self.equals(recipient)) { // sending to self
              final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessPairFor(recipient);
              SentTranscriptMessage transcript =
                  new SentTranscriptMessage(Optional.of(recipient.getAddress()), message.getTimestamp(), Optional.of(message), message.getExpiresInSeconds(),
                                            Collections.singletonMap(recipient.getAddress(), unidentifiedAccess.isPresent()), false, Optional.empty(), Set.of());
              SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);
              try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
                messageSender.sendSyncMessage(syncMessage, unidentifiedAccess);
              }
              //              results.add(SendMessageResult.success(recipient, devices, false, unidentifiedAccess.isPresent(), true, (System.currentTimeMillis() - start),
              //              Optional.absent());
            } else {
              try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
                results.add(messageSender.sendDataMessage(recipient.getAddress(), getAccessPairFor(recipient), ContentHint.DEFAULT, message, IndividualSendEventsLogger.INSTANCE));
              } finally {
                logger.debug("send complete");
              }
            }
          } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
            if (e.getIdentityKey() != null) {
              account.getProtocolStore().handleUntrustedIdentityException(e);
            }
            results.add(SendMessageResult.identityFailure(recipient.getAddress(), e.getIdentityKey()));
          }
        }
        return results;
      }
    } finally {
      if (message != null && message.isEndSession()) {
        for (Recipient recipient : recipients) {
          handleEndSession(recipient);
        }
      }
    }
  }

  private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException, ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, ProtocolLegacyMessageException,
             ProtocolNoSessionException, ProtocolInvalidVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, UnsupportedDataMessageException,
             org.signal.libsignal.protocol.UntrustedIdentityException, InvalidMessageStructureException, IOException, SQLException, InterruptedException {
    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      CertificateValidator certificateValidator = new CertificateValidator(unidentifiedSenderTrustRoot);
      SignalServiceCipher cipher =
          new SignalServiceCipher(self.getAddress(), account.getDeviceId(), account.getProtocolStore(), dependencies.getSessionLock(), certificateValidator);
      Semaphore sem = new Semaphore(1);
      int watchdogTime = Config.getDecryptionTimeout();
      if (watchdogTime > 0) {
        sem.acquire();
        Thread t = new Thread(() -> {
          // a watchdog thread that will make signald exit if decryption takes too long. This behavior is suboptimal, but
          // without this it just hangs and breaks in difficult to detect ways.
          try {
            boolean decryptFinished = sem.tryAcquire(watchdogTime, TimeUnit.SECONDS);
            if (!decryptFinished) {
              logger.error("took over {} seconds to decrypt, exiting", watchdogTime);
              System.exit(101);
            }
            sem.release();
          } catch (InterruptedException e) {
            logger.error("error in decryption watchdog thread", e);
            Sentry.captureException(e);
          }
        }, "DecryptWatchdogTimer");

        t.start();
      }

      Histogram.Timer timer = messageDecryptionTime.labels(account.getUUID().toString()).startTimer();
      try {
        return cipher.decrypt(envelope);
      } catch (ProtocolUntrustedIdentityException e) {
        if (e.getCause() instanceof org.signal.libsignal.protocol.UntrustedIdentityException) {
          org.signal.libsignal.protocol.UntrustedIdentityException identityException = (org.signal.libsignal.protocol.UntrustedIdentityException)e.getCause();
          account.getProtocolStore().saveIdentity(identityException.getName(), identityException.getUntrustedIdentity(), Config.getNewKeyTrustLevel());
          throw identityException;
        }
        throw e;
      } catch (SelfSendException e) {
        logger.debug("Dropping UD message from self (because that's what Signal Android does)");
        return null;
      } catch (ProtocolInvalidKeyIdException | ProtocolInvalidKeyException | ProtocolNoSessionException | ProtocolInvalidMessageException e) {
        logger.debug("Failed to decrypt incoming message: {}", e.getMessage());
        Database db = account.getDB();
        Recipient sender = db.RecipientsTable.get(e.getSender());
        boolean senderCapability = db.ProfileCapabilitiesTable.get(sender, IProfileCapabilitiesTable.SENDER_KEY);
        boolean selfCapability = db.ProfileCapabilitiesTable.get(account.getSelf(), IProfileCapabilitiesTable.SENDER_KEY);
        if (e.getSenderDevice() != account.getDeviceId() && senderCapability && selfCapability) {
          logger.debug("Received invalid message, requesting message resend.");
          BackgroundJobRunnerThread.queue(new SendRetryMessageRequestJob(account, e, envelope));
        } else {
          logger.debug("Received invalid message, queuing reset session action.");
          BackgroundJobRunnerThread.queue(new ResetSessionJob(account, sender));
        }
        throw e;
      } catch (ProtocolDuplicateMessageException e) {
        logger.debug("dropping duplicate message");
        return null;
      } finally {
        if (watchdogTime > 0) {
          sem.release();
        }
        double duration = timer.observeDuration();
        logger.debug("message decrypted in {} seconds", duration);
      }
    }
  }

  private void handleEndSession(Recipient address) { account.getProtocolStore().deleteAllSessions(address); }

  public List<SendMessageResult> send(SignalServiceDataMessage.Builder message, Recipient recipient, GroupIdentifier recipientGroupId, List<Recipient> members)
      throws IOException, InvalidRecipientException, UnknownGroupException, SQLException, NoSendPermissionException, InvalidInputException {
    if (recipientGroupId != null && recipient == null) {
      Optional<IGroupsTable.IGroup> groupOptional = Database.Get(account.getACI()).GroupsTable.get(recipientGroupId);
      if (groupOptional.isEmpty()) {
        throw new UnknownGroupException();
      }
      var group = groupOptional.get();
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

      return sendGroupV2Message(message, group.getSignalServiceGroupV2(), members);
    } else if (recipient != null && recipientGroupId == null) {
      List<Recipient> r = new ArrayList<>();
      r.add(recipient);
      return sendMessage(message, r);
    } else {
      throw new InvalidRecipientException();
    }
  }

  @Deprecated
  public SignalServiceMessageReceiver getMessageReceiver() {
    return dependencies.getMessageReceiver();
  }

  public SignalServiceMessageSender getMessageSender() { return dependencies.getMessageSender(); }

  public interface ReceiveMessageHandler {
    void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e) throws SQLException;
  }

  private List<Job> handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, Recipient source, Recipient destination, boolean ignoreAttachments)
      throws MissingConfigurationException, IOException, VerificationFailedException, SQLException, InvalidInputException {

    List<Job> jobs = new ArrayList<>();
    if (message.getGroupContext().isPresent()) {
      SignalServiceGroupContext groupContext = message.getGroupContext().get();
      if (groupContext.getGroupV2().isPresent()) {
        SignalServiceGroupV2 group = message.getGroupContext().get().getGroupV2().get();
        var localState = Database.Get(account.getACI()).GroupsTable.get(group);

        if (localState.isEmpty() || localState.get().getRevision() < group.getRevision()) {
          try {
            account.getGroups().getGroup(group);
          } catch (InvalidGroupStateException | InvalidProxyException | NoSuchAccountException | ServerNotFoundException e) {
            logger.warn("error fetching state of incoming group", e);
          }
        }
      }
    } else {
      account.getDB().ContactsTable.update(isSync ? destination : source, null, null, null, message.getExpiresInSeconds(), null);
    }

    if (message.isEndSession()) {
      handleEndSession(isSync ? destination : source);
    }

    if (message.getAttachments().isPresent() && !ignoreAttachments) {
      for (SignalServiceAttachment attachment : message.getAttachments().get()) {
        if (attachment.isPointer()) {
          try {
            retrieveAttachment(attachment.asPointer());
          } catch (IOException | InvalidMessageException e) {
            logger.warn("Failed to retrieve attachment (" + attachment.asPointer().getRemoteId() + "): " + e.getMessage());
          }
        }
      }
    }

    if (message.getPreviews().isPresent() && !ignoreAttachments) {
      for (SignalServicePreview preview : message.getPreviews().get()) {
        if (preview.getImage().isPresent()) {
          SignalServiceAttachment attachment = preview.getImage().get();
          if (attachment.isPointer()) {
            try {
              retrieveAttachment(attachment.asPointer());
            } catch (IOException | InvalidMessageException e) {
              logger.warn("Failed to retrieve preview attachment ({}): {}", attachment.asPointer().getRemoteId(), e);
            }
          }
        }
      }
    }

    if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
      final ProfileKey profileKey;
      try {
        profileKey = new ProfileKey(message.getProfileKey().get());
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
      account.getDB().ProfileKeysTable.setProfileKey(source, profileKey);
      RefreshProfileJob.queueIfNeeded(account, source);
    }

    if (message.getSticker().isPresent()) {
      DownloadStickerJob job = new DownloadStickerJob(this, message.getSticker().get());
      if (job.needsDownload()) {
        try {
          job.run();
        } catch (NoSuchAccountException | InvalidMessageException e) {
          logger.error("Sticker failed to download");
          Sentry.captureException(e);
        }
      }
    }

    return jobs;
  }

  public void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments)
      throws IOException, MissingConfigurationException, SQLException, InvalidInputException, NoSuchAccountException {

    // Attempt to load messages from legacy on-disk storage
    final File cachePath = new File(getMessageCachePath());
    if (!cachePath.exists()) {
      return;
    }
    for (final File dir : cachePath.listFiles()) {
      if (!dir.isDirectory()) {
        continue;
      }

      for (final File fileEntry : dir.listFiles()) {
        if (!fileEntry.isFile()) {
          continue;
        }
        SignalServiceEnvelope envelope;
        try {
          envelope = loadEnvelope(fileEntry);
          if (envelope == null) {
            continue;
          }
        } catch (IOException e) {
          Files.delete(fileEntry.toPath());
          logger.catching(e);
          continue;
        }
        SignalServiceContent content = null;
        Exception exception = null;
        if (!envelope.isReceipt()) {
          try {
            content = decryptMessage(envelope);
          } catch (Exception e) {
            exception = e;
          }
          if (exception == null && content != null) {
            try {
              handleMessage(envelope, content, ignoreAttachments);
            } catch (VerificationFailedException | InvalidKeyException | InvalidMessageException e) {
              logger.catching(e);
              Sentry.captureException(e);
            }
          }
        }
        if (exception != null || content != null) {
          handler.handleMessage(envelope, content, exception);
        }
        try {
          Files.delete(fileEntry.toPath());
        } catch (IOException e) {
          logger.warn("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
        }
      }
      // Try to delete directory if empty
      dir.delete();
    }

    while (true) {
      var storedEnvelope = account.getDB().MessageQueueTable.nextEnvelope();
      if (storedEnvelope == null) {
        break;
      }
      SignalServiceEnvelope envelope = storedEnvelope.envelope;

      try {
        SignalServiceContent content = null;
        Exception exception = null;
        if (!envelope.isReceipt()) {
          try {
            content = decryptMessage(envelope);
          } catch (Exception e) {
            exception = e;
          }
          if (exception == null && content != null) {
            try {
              handleMessage(envelope, content, ignoreAttachments);
            } catch (VerificationFailedException | InvalidKeyException | InvalidMessageException e) {
              logger.catching(e);
              Sentry.captureException(e);
            }
          }
        }
        if (exception != null || content != null) {
          handler.handleMessage(envelope, content, exception);
        }
      } finally {
        account.getDB().MessageQueueTable.deleteEnvelope(storedEnvelope.databaseId);
      }
    }
  }

  public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler)
      throws IOException, MissingConfigurationException, VerificationFailedException, SQLException, InvalidInputException, NoSuchAccountException {
    retryFailedReceivedMessages(handler, ignoreAttachments);

    SignalWebSocket websocket = dependencies.getWebSocket();

    logger.debug("connecting to websocket");
    websocket.connect();

    var messageQueueTable = Database.Get(aci).MessageQueueTable;

    try {
      while (true) {
        SignalServiceEnvelope envelope;
        MutableLong databaseId = new MutableLong();
        try {
          Optional<SignalServiceEnvelope> result = websocket.readOrEmpty(unit.toMillis(timeout), encryptedEnvelope -> {
            // store message on disk, before acknowledging receipt to the server
            try {
              long id = messageQueueTable.storeEnvelope(encryptedEnvelope);
              databaseId.setValue(id);
            } catch (SQLException e) {
              logger.warn("Failed to store encrypted message in database, ignoring: " + e.getMessage());
            }
          });
          if (result.isPresent()) {
            envelope = result.get();
          } else {
            continue;
          }
        } catch (TimeoutException e) {
          if (returnOnTimeout) {
            return;
          }
          continue;
        }

        SignalServiceContent content = null;
        Exception exception = null;

        if (!envelope.isReceipt()) {
          try {
            content = decryptMessage(envelope);
          } catch (Exception e) {
            exception = e;
          }
          if (exception == null && content != null) {
            try {
              handleMessage(envelope, content, ignoreAttachments);
            } catch (InvalidKeyException | InvalidMessageException e) {
              logger.catching(e);
              Sentry.captureException(e);
            }
          }
        }
        if (exception != null || content != null) {
          handler.handleMessage(envelope, content, exception);
        }
        try {
          Long id = databaseId.getValue();
          if (id != null) {
            messageQueueTable.deleteEnvelope(id);
          }
        } catch (SQLException e) {
          logger.error("failed to remove cached message from database");
          Sentry.captureException(e);
        }
      }
    } finally {
      logger.debug("disconnecting websocket");
      websocket.disconnect();
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments)
      throws IOException, MissingConfigurationException, VerificationFailedException, SQLException, InvalidInputException, InvalidKeyException, InvalidMessageException {
    List<Job> jobs = new ArrayList<>();
    if (content == null) {
      return;
    }

    Database db = Database.Get(aci);
    var source = db.RecipientsTable.get((envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) ? envelope.getSourceAddress() : content.getSender());
    if (content.getSenderKeyDistributionMessage().isPresent()) {
      logger.debug("handling sender key distribution message from {}", content.getSender().getIdentifier());
      getMessageSender().processSenderKeyDistributionMessage(new SignalProtocolAddress(content.getSender().getIdentifier(), content.getSenderDevice()),
                                                             content.getSenderKeyDistributionMessage().get());
    }

    if (content.getDecryptionErrorMessage().isPresent()) {
      DecryptionErrorMessage message = content.getDecryptionErrorMessage().get();
      logger.debug("Received a decryption error message (resend request for {})", message.getTimestamp());
      if (message.getRatchetKey().isPresent()) {
        int sourceDeviceId = (envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) ? envelope.getSourceDevice() : content.getSenderDevice();
        if (message.getDeviceId() == account.getDeviceId() && account.getProtocolStore().isCurrentRatchetKey(source, sourceDeviceId, message.getRatchetKey().get())) {
          logger.debug("Resetting the session with sender");
          jobs.add(new ResetSessionJob(account, source));
        }
      } else {
        logger.debug("Reset shared sender keys with this recipient");
        db.SenderKeySharedTable.deleteSharedWith(source);
      }
    }

    if (content.getDataMessage().isPresent()) {
      if (content.isNeedsReceipt()) {
        jobs.add(new SendDeliveryReceiptJob(this, source, content.getTimestamp()));
      }
      SignalServiceDataMessage message = content.getDataMessage().get();
      jobs.addAll(handleSignalServiceDataMessage(message, false, source, self, ignoreAttachments));
    }

    if (envelope.isPreKeySignalMessage()) {
      jobs.add(new RefreshPreKeysJob(aci));
    }

    if (content.getSyncMessage().isPresent()) {
      account.setMultiDevice(true);

      SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

      if (syncMessage.getSent().isPresent()) {
        SentTranscriptMessage sentMessage = syncMessage.getSent().get();
        if (sentMessage.getDataMessage().isPresent()) {
          SignalServiceDataMessage message = sentMessage.getDataMessage().get();

          Recipient sendMessageRecipient = null;
          if (syncMessage.getSent().get().getDestination().isPresent()) {
            sendMessageRecipient = db.RecipientsTable.get(syncMessage.getSent().get().getDestination().get());
          }
          jobs.addAll(handleSignalServiceDataMessage(message, true, source, sendMessageRecipient, ignoreAttachments));
        }
      }

      if (syncMessage.getRequest().isPresent() && account.getDeviceId() == SignalServiceAddress.DEFAULT_DEVICE_ID) {
        RequestMessage rm = syncMessage.getRequest().get();
        if (rm.isContactsRequest()) {
          jobs.add(new SendContactsSyncJob(account));
        }
      }

      if (syncMessage.getBlockedList().isPresent()) {
        // TODO store list of blocked numbers
        logger.info("received list of blocked users from device " + content.getSenderDevice());
      }

      if (syncMessage.getContacts().isPresent()) {
        File tmpFile = null;
        try {
          tmpFile = Util.createTempFile();
          final ContactsMessage contactsMessage = syncMessage.getContacts().get();
          try (InputStream attachmentAsStream = retrieveAttachmentAsStream(contactsMessage.getContactsStream().asPointer(), tmpFile)) {
            DeviceContactsInputStream s = new DeviceContactsInputStream(attachmentAsStream);
            if (contactsMessage.isComplete()) {
              logger.debug("contact sync includes complete set of contacts, clearly local contact list before processing");
              db.ContactsTable.clear();
            }
            DeviceContact c;
            while ((c = s.read()) != null) {
              Recipient recipient = db.RecipientsTable.get(c.getAddress());
              db.ContactsTable.update(c);
              if (c.getAvatar().isPresent()) {
                retrieveContactAvatarAttachment(c.getAvatar().get(), recipient);
              }
              if (c.getProfileKey().isPresent()) {
                db.ProfileKeysTable.setProfileKey(recipient, c.getProfileKey().get());
              }
            }
          }
          logger.info("received contacts from device " + content.getSenderDevice());
        } catch (Exception e) {
          logger.catching(e);
        } finally {
          if (tmpFile != null) {
            try {
              Files.delete(tmpFile.toPath());
            } catch (IOException e) {
              logger.warn("Failed to delete received contacts temp file “" + tmpFile + "”: " + e.getMessage());
            }
          }
        }
      }

      if (syncMessage.getVerified().isPresent()) {
        VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
        Recipient destination = db.RecipientsTable.get(verifiedMessage.getDestination());
        TrustLevel trustLevel = TrustLevel.fromVerifiedState(verifiedMessage.getVerified());
        account.getProtocolStore().saveIdentity(destination, verifiedMessage.getIdentityKey(), trustLevel);
        logger.info("received verified state update from device {}", content.getSenderDevice());
      }

      if (syncMessage.getKeys().isPresent()) {
        KeysMessage keysMessage = syncMessage.getKeys().get();
        if (keysMessage.getStorageService().isPresent()) {
          StorageKey storageKey = keysMessage.getStorageService().get();
          account.setStorageKey(storageKey);
          BackgroundJobRunnerThread.queue(new SyncStorageDataJob(account));
        }
      }

      if (syncMessage.getFetchType().isPresent()) {
        switch (syncMessage.getFetchType().get()) {
        case LOCAL_PROFILE:
          BackgroundJobRunnerThread.queue(new RefreshProfileJob(account, self));
          break;
        case STORAGE_MANIFEST:
          BackgroundJobRunnerThread.queue(new SyncStorageDataJob(account));
          break;
        }
      }

      if (syncMessage.getPniIdentity().isPresent()) {
        SignalServiceProtos.SyncMessage.PniIdentity pniIdentity = syncMessage.getPniIdentity().get();
        IdentityKey pniIdentityKey = new IdentityKey(pniIdentity.getPublicKey().toByteArray());
        ECPrivateKey pniPrivateKey = Curve.decodePrivatePoint(pniIdentity.getPrivateKey().toByteArray());
        account.setPNIIdentityKeyPair(new IdentityKeyPair(pniIdentityKey, pniPrivateKey));
        logger.info("received PNI identity key from device {}", content.getSenderDevice());
      }
    }

    if (content.getStoryMessage().isPresent()) {
      SignalServiceStoryMessage story = content.getStoryMessage().get();
      if (story.getFileAttachment().isPresent()) {
        retrieveAttachment(story.getFileAttachment().get().asPointer());
      }
    }

    for (Job job : jobs) {
      BackgroundJobRunnerThread.queue(job);
    }
  }

  private SignalServiceEnvelope loadEnvelope(File file) throws IOException {
    logger.debug("Loading cached envelope from " + file.toString());
    try (FileInputStream f = new FileInputStream(file)) {
      DataInputStream in = new DataInputStream(f);
      int version = in.readInt();
      if (version > 4) {
        return null;
      }
      int type = in.readInt();
      String source = in.readUTF();
      ACI sourceACI = null;
      if (version >= 3) {
        sourceACI = ACI.parseOrNull(in.readUTF());
      }
      int sourceDevice = in.readInt();
      if (version == 1) {
        // read legacy relay field
        in.readUTF();
      }
      long timestamp = in.readLong();
      byte[] content = null;
      int contentLen = in.readInt();
      if (contentLen > 0) {
        content = new byte[contentLen];
        in.readFully(content);
      }
      byte[] legacyMessage = null;
      int legacyMessageLen = in.readInt();
      if (legacyMessageLen > 0) {
        legacyMessage = new byte[legacyMessageLen];
        in.readFully(legacyMessage);
      }
      long serverReceivedTimestamp = 0;
      String uuid = null;
      if (version >= 2) {
        serverReceivedTimestamp = in.readLong();
        uuid = in.readUTF();
        if ("".equals(uuid)) {
          uuid = null;
        }
      }
      long serverDeliveredTimestamp = 0;
      if (version >= 4) {
        serverDeliveredTimestamp = in.readLong();
      }
      Optional<SignalServiceAddress> sourceAddress = sourceACI == null && source.isEmpty() ? Optional.empty() : Optional.of(new SignalServiceAddress(sourceACI, source));
      return new SignalServiceEnvelope(type, sourceAddress, sourceDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid, null);
    }
  }

  public File getContactAvatarFile(Recipient recipient) {
    SignalServiceAddress address = recipient.getAddress();
    if (address.getNumber().isPresent()) {
      return new File(avatarsPath, "contact-" + address.getNumber().get());
    }
    return new File(avatarsPath, "contact-" + address.getIdentifier());
  }

  private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, Recipient recipient) throws IOException, InvalidMessageException, MissingConfigurationException {
    createPrivateDirectories(avatarsPath);
    if (attachment.isPointer()) {
      SignalServiceAttachmentPointer pointer = attachment.asPointer();
      return retrieveAttachment(pointer, getContactAvatarFile(recipient), false);
    } else {
      SignalServiceAttachmentStream stream = attachment.asStream();
      return retrieveAttachment(stream, getContactAvatarFile(recipient));
    }
  }

  public File getGroupAvatarFile(byte[] groupId) { return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_")); }

  public File getAttachmentFile(String attachmentId) { return new File(attachmentsPath, attachmentId); }

  public static File getStickerFile(SignalServiceDataMessage.Sticker sticker) {
    String packID = Hex.toStringCondensed(sticker.getPackId());
    String stickerID = String.valueOf(sticker.getStickerId());
    return new File(stickersPath + "/" + packID, stickerID);
  }

  private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException, MissingConfigurationException {
    createPrivateDirectories(attachmentsPath);
    return retrieveAttachment(pointer, getAttachmentFile(pointer.getRemoteId().toString()), true);
  }

  private File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException {
    InputStream input = stream.getInputStream();

    try (OutputStream output = new FileOutputStream(outputFile)) {
      byte[] buffer = new byte[4096];
      int read;

      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    } catch (FileNotFoundException e) {
      logger.catching(e);
      return null;
    }
    return outputFile;
  }

  public File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    if (storePreview && pointer.getPreview().isPresent()) {
      File previewFile = new File(outputFile + ".preview");
      try (OutputStream output = new FileOutputStream(previewFile)) {
        byte[] preview = pointer.getPreview().get();
        output.write(preview, 0, preview.length);
      } catch (FileNotFoundException e) {
        logger.catching(e);
        return null;
      }
    }

    final SignalServiceMessageReceiver messageReceiver = dependencies.getMessageReceiver();

    File tmpFile = Util.createTempFile();
    try (InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE)) {
      try (OutputStream output = new FileOutputStream(outputFile)) {
        byte[] buffer = new byte[4096];
        int read;

        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
        }
      } catch (FileNotFoundException e) {
        logger.catching(e);
        return null;
      }
    } finally {
      try {
        Files.delete(tmpFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete received attachment temp file “" + tmpFile + "”: " + e.getMessage());
      }
    }
    return outputFile;
  }

  private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer, File tmpFile) throws IOException, InvalidMessageException, MissingConfigurationException {
    final SignalServiceMessageReceiver messageReceiver = dependencies.getMessageReceiver();
    return messageReceiver.retrieveAttachment(pointer, tmpFile, ServiceConfig.MAX_ATTACHMENT_SIZE);
  }

  private void sendVerifiedMessage(Recipient destination, IdentityKey identityKey, TrustLevel trustLevel)
      throws IOException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException, SQLException {
    VerifiedMessage verifiedMessage = new VerifiedMessage(destination.getAddress(), identityKey, trustLevel.toVerifiedState(), System.currentTimeMillis());
    sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
  }

  public List<IIdentityKeysTable.IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException { return account.getProtocolStore().getIdentities(); }

  public List<IIdentityKeysTable.IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException {
    return account.getProtocolStore().getIdentities(recipient);
  }

  public boolean trustIdentity(Recipient recipient, byte[] fingerprint, TrustLevel level) throws SQLException, InvalidKeyException {
    var ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (var id : ids) {
      if (!Arrays.equals(id.getKey().serialize(), fingerprint)) {
        continue;
      }

      account.getProtocolStore().saveIdentity(recipient, id.getKey(), level);
      try {
        sendVerifiedMessage(recipient, id.getKey(), level);
      } catch (IOException | org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
        logger.catching(e);
      }
      return true;
    }
    return false;
  }

  public boolean trustIdentitySafetyNumber(Recipient recipient, String safetyNumber, TrustLevel level) throws SQLException, InvalidKeyException {
    var ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (var id : ids) {
      if (!safetyNumber.equals(SafetyNumberHelper.computeSafetyNumber(self, getIdentity(), recipient, id.getKey()))) {
        continue;
      }
      account.getProtocolStore().saveIdentity(recipient, id.getKey(), level);

      try {
        sendVerifiedMessage(recipient, id.getKey(), level);
      } catch (IOException | org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
        logger.catching(e);
      }
      return true;
    }
    return false;
  }

  public boolean trustIdentitySafetyNumber(Recipient recipient, byte[] scannedFingerprintData, TrustLevel level)
      throws FingerprintVersionMismatchException, FingerprintParsingException, SQLException, InvalidKeyException {
    var ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (var id : ids) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(self, getIdentity(), recipient, id.getKey());
      if (fingerprint == null) {
        throw new IllegalArgumentException("Fingerprint is null");
      }
      if (!fingerprint.getScannableFingerprint().compareTo(scannedFingerprintData)) {
        continue;
      }

      account.getProtocolStore().saveIdentity(recipient, id.getKey(), level);
      try {
        sendVerifiedMessage(recipient, id.getKey(), level);
      } catch (IOException | org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
        logger.catching(e);
      }
      return true;
    }
    return false;
  }

  private List<Optional<UnidentifiedAccessPair>> getAccessPairFor(Collection<Recipient> recipients) {
    List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      result.add(getAccessPairFor(recipient));
    }
    return result;
  }

  private Optional<UnidentifiedAccessPair> getAccessPairFor(Recipient recipient) {
    try {
      return new UnidentifiedAccessUtil(aci).getAccessPairFor(recipient);
    } catch (SQLException | IOException | NoSuchAccountException | ServerNotFoundException | InvalidProxyException e) {
      logger.error("unexpected error getting UnidentifiedAccessPair: ", e);
      Sentry.captureException(e);
      return Optional.empty();
    }
  }

  public void deleteAccount() throws IOException, SQLException {
    synchronized (managers) { managers.remove(aci.toString()); }
  }

  public SignalServiceConfiguration getServiceConfiguration() { return serviceConfiguration; }
}
