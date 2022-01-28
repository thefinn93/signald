/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */
package io.finn.signald;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.clientprotocol.v1.JsonGroupV2Info;
import io.finn.signald.db.*;
import io.finn.signald.exceptions.*;
import io.finn.signald.jobs.*;
import io.finn.signald.storage.*;
import io.finn.signald.util.AttachmentUtil;
import io.finn.signald.util.MutableLong;
import io.finn.signald.util.SafetyNumberHelper;
import io.prometheus.client.Histogram;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.metadata.*;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.storageservice.protos.groups.local.DecryptedTimer;
import org.signal.storageservice.protos.groups.local.EnabledState;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.groups.GroupIdentifier;
import org.signal.zkgroup.groups.GroupSecretParams;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.FingerprintParsingException;
import org.whispersystems.libsignal.fingerprint.FingerprintVersionMismatchException;
import org.whispersystems.libsignal.protocol.DecryptionErrorMessage;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.*;
import org.whispersystems.signalservice.api.crypto.*;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.DeviceNameUtil;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.util.Base64;

public class Manager {
  private final Logger logger;
  private final SignalServiceConfiguration serviceConfiguration;
  private final ECPublicKey unidentifiedSenderTrustRoot;
  private final static int ACCOUNT_REFRESH_VERSION = 5;

  private static final ConcurrentHashMap<String, Manager> managers = new ConcurrentHashMap<>();
  private static final Histogram messageDecryptionTime =
      Histogram.build().name(BuildConfig.NAME + "_message_decryption_time").help("Time (in seconds) to decrypt incoming messages").labelNames("account_uuid").register();

  private static String dataPath;
  private static String attachmentsPath;
  private static String avatarsPath;
  private static String stickersPath;

  private final AccountData accountData;
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
    Logger logger = LogManager.getLogger("manager");
    AccountData accountData;
    Manager m;
    synchronized (managers) {
      if (managers.containsKey(aci.toString())) {
        return managers.get(aci.toString());
      }
      accountData = AccountData.load(AccountsTable.getFile(aci));
      m = new Manager(aci, accountData);
      managers.put(aci.toString(), m);
    }

    if (!offline) {
      RefreshPreKeysJob.runIfNeeded(aci, m);
      m.refreshAccountIfNeeded();
      try {
        m.getRecipientProfileKeyCredential(m.self);
      } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
      }
    }
    logger.info("created a manager for " + m.self.toRedactedString());
    return m;
  }

  public static Manager get(String e164) throws NoSuchAccountException, SQLException, InvalidProxyException, ServerNotFoundException, InvalidKeyException, IOException {
    UUID uuid = AccountsTable.getUUID(e164);
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

  Manager(ACI aci, AccountData accountData) throws IOException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, NoSuchAccountException {
    this.aci = aci;
    account = new Account(aci);
    this.accountData = accountData;
    self = new RecipientsTable(aci).get(aci);
    logger = LogManager.getLogger("manager-" + Util.redact(aci.toString()));
    ServersTable.Server server = AccountsTable.getServer(aci);
    serviceConfiguration = server.getSignalServiceConfiguration();
    unidentifiedSenderTrustRoot = server.getUnidentifiedSenderRoot();
    dependencies = SignalDependencies.get(aci);
    logger.info("Created a manager for " + Util.redact(aci.toString()));
    synchronized (managers) { managers.put(aci.toString(), this); }
  }

  public static void setDataPath() throws IOException {
    LogManager.getLogger().debug("Using data folder {}", Config.getDataPath());
    dataPath = Config.getDataPath() + "/data";
    AccountData.setDataPath(dataPath);
    attachmentsPath = Config.getDataPath() + "/attachments";
    avatarsPath = Config.getDataPath() + "/avatars";
    stickersPath = Config.getDataPath() + "/stickers";
    GroupsTable.setGroupAvatarPath(avatarsPath);
    createPrivateDirectories(dataPath);
  }

  public Account getAccount() { return account; }

  public UUID getUUID() { return aci.uuid(); }

  public ACI getACI() { return aci; }

  public Recipient getOwnRecipient() { return self; }

  public IdentityKey getIdentity() { return account.getProtocolStore().getIdentityKeyPair().getPublicKey(); }

  public static String getFileName(String username) { return dataPath + "/" + username; }

  private String getMessageCachePath() { return dataPath + "/" + accountData.getLegacyUsername() + ".d/msg-cache"; }

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
      try {
        String name = URLDecoder.decode(param.split("=")[0], "UTF-8");
        String value = URLDecoder.decode(param.split("=")[1], "UTF-8");
        map.put(name, value);
      } catch (UnsupportedEncodingException e) {
        LogManager.getLogger().error("error preparing device link URL", e);
      }
    }
    return map;
  }

  public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException, InvalidInputException {
    Map<String, String> query = getQueryMap(linkUri.getRawQuery());
    String deviceIdentifier = query.get("uuid");
    String publicKeyEncoded = query.get("pub_key");

    if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
      throw new RuntimeException("Invalid device link uri");
    }

    ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

    addDevice(deviceIdentifier, deviceKey);
  }

  private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException, InvalidInputException {
    IdentityKeyPair identityKeyPair = account.getProtocolStore().getIdentityKeyPair();
    SignalServiceAccountManager accountManager = dependencies.getAccountManager();
    String verificationCode = accountManager.getNewDeviceVerificationCode();

    Optional<byte[]> profileKeyOptional;
    ProfileKey profileKey = accountData.getProfileKey();
    profileKeyOptional = Optional.of(profileKey.serialize());
    accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, profileKeyOptional, verificationCode);
  }

  private List<PreKeyRecord> generatePreKeys() throws IOException, SQLException {
    List<PreKeyRecord> records = new LinkedList<>();

    DatabaseProtocolStore protocolStore = account.getProtocolStore();
    for (int i = 0; i < ServiceConfig.PREKEY_BATCH_SIZE; i++) {
      int preKeyId = (account.getPreKeyIdOffset() + i) % Medium.MAX_VALUE;
      ECKeyPair keyPair = Curve.generateKeyPair();
      PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

      protocolStore.storePreKey(preKeyId, record);
      records.add(record);
    }

    account.setPreKeyIdOffset((account.getPreKeyIdOffset() + ServiceConfig.PREKEY_BATCH_SIZE + 1) % Medium.MAX_VALUE);
    accountData.save();

    return records;
  }

  private SignedPreKeyRecord generateSignedPreKey() throws SQLException {
    try {
      ECKeyPair keyPair = Curve.generateKeyPair();
      byte[] signature = Curve.calculateSignature(account.getIdentityKeyPair().getPrivateKey(), keyPair.getPublicKey().serialize());
      int signedPreKeyId = account.getNextSignedPreKeyId();
      SignedPreKeyRecord record = new SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), keyPair, signature);
      account.getProtocolStore().storeSignedPreKey(signedPreKeyId, record);
      account.setNextSignedPreKeyId((signedPreKeyId + 1) % Medium.MAX_VALUE);

      return record;
    } catch (InvalidKeyException e) {
      throw new AssertionError(e);
    }
  }

  public void refreshPreKeys() throws IOException, SQLException {
    List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
    SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey();
    dependencies.getAccountManager().setPreKeys(account.getIdentityKeyPair().getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
  }

  private static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException { return createAttachment(attachmentFile, Optional.absent()); }

  private static SignalServiceAttachmentStream createAttachment(File attachmentFile, Optional<String> caption) throws IOException {
    InputStream attachmentStream = new FileInputStream(attachmentFile);
    final long attachmentSize = attachmentFile.length();
    String mime = Files.probeContentType(attachmentFile.toPath());
    if (mime == null) {
      mime = "application/octet-stream";
    }
    // TODO mabybe add a parameter to set the voiceNote, preview, and caption option
    return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, false, false, Optional.absent(), 0, 0,
                                             System.currentTimeMillis(), caption, Optional.absent(), null, null, Optional.absent());
  }

  public Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
    File file = getGroupAvatarFile(groupId);
    if (!file.exists()) {
      return Optional.absent();
    }

    return Optional.of(createAttachment(file));
  }

  public Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(Recipient recipient) throws IOException {
    File file = getContactAvatarFile(recipient);
    if (!file.exists()) {
      return Optional.absent();
    }

    return Optional.of(createAttachment(file));
  }

  private GroupInfo getGroupForSending(byte[] groupId) throws GroupNotFoundException, NotAGroupMemberException {
    GroupInfo g = accountData.groupStore.getGroup(groupId);
    if (g == null) {
      throw new GroupNotFoundException(groupId);
    }

    if (!g.isMember(self.getAddress())) {
      throw new NotAGroupMemberException(groupId, g.name);
    }

    return g;
  }

  public List<GroupInfo> getV1Groups() { return accountData.groupStore.getGroups(); }

  public List<JsonGroupV2Info> getGroupsV2Info() throws SQLException {
    List<JsonGroupV2Info> groups = new ArrayList<>();
    for (GroupsTable.Group g : account.getGroupsTable().getAll()) {
      groups.add(g.getJsonGroupV2Info());
    }
    return groups;
  }

  public List<SendMessageResult> sendGroupV2Message(SignalServiceDataMessage.Builder message, GroupsTable.Group group) throws IOException, SQLException {

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

  public List<SendMessageResult> sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, NotAGroupMemberException, SQLException {
    SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT).withId(groupId).build();

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);

    final GroupInfo g = getGroupForSending(groupId);
    g.members.remove(accountData.address);
    accountData.groupStore.updateGroup(g);

    List<Recipient> members = getRecipientsTable().get(g.getMembers());
    return sendMessage(messageBuilder, members);
  }

  public GroupInfo sendUpdateGroupMessage(byte[] groupId, String name, Collection<Recipient> members, String avatarFile)
      throws IOException, GroupNotFoundException, NotAGroupMemberException, SQLException {
    GroupInfo g;
    if (groupId == null) {
      // Create new group
      g = new GroupInfo(Util.getSecretBytes(16));
      g.addMember(accountData.address);
    } else {
      g = getGroupForSending(groupId);
    }

    if (name != null) {
      g.name = name;
    }

    if (members != null) {
      for (Recipient member : members) {
        for (JsonAddress m : g.members) {
          if (m.matches(member.getAddress())) {
            continue;
          }
          g.addMember(new JsonAddress(member.getAddress()));
        }
      }
    }

    if (avatarFile != null) {
      createPrivateDirectories(avatarsPath);
      File aFile = getGroupAvatarFile(g.groupId);
      Files.copy(Paths.get(avatarFile), aFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    accountData.groupStore.updateGroup(g);

    SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

    // Don't send group message to ourself
    final List<Recipient> membersSend = getRecipientsTable().get(g.getMembers());
    membersSend.remove(self);
    sendMessage(messageBuilder, membersSend);
    return g;
  }

  public SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfo g) {
    SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE).withId(g.groupId).withName(g.name).withMembers(g.getMembers());

    File aFile = getGroupAvatarFile(g.groupId);
    if (aFile.exists()) {
      try {
        group.withAvatar(createAttachment(aFile));
      } catch (IOException e) {
        logger.warn("Unable to attach group avatar:" + aFile.toString(), e);
      }
    }

    return SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());
  }

  // set expiration for a v1 group
  public List<SendMessageResult> setExpiration(byte[] groupId, int expiresInSeconds) throws IOException, GroupNotFoundException, NotAGroupMemberException, SQLException {
    if (groupId == null) {
      return null;
    }
    GroupInfo g = getGroupForSending(groupId);
    g.messageExpirationTime = expiresInSeconds;
    accountData.groupStore.updateGroup(g);
    accountData.save();
    SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);
    messageBuilder.asExpirationUpdate().withExpiration(expiresInSeconds);
    List<Recipient> members = getRecipientsTable().get(g.getMembers());
    return sendMessage(messageBuilder, members);
  }

  // set expiration for a 1-to-1 conversation
  public List<SendMessageResult> setExpiration(Recipient recipient, int expiresInSeconds) throws IOException, SQLException {
    ContactStore.ContactInfo contact = accountData.contactStore.getContact(recipient);
    contact.messageExpirationTime = expiresInSeconds;
    accountData.contactStore.updateContact(contact);
    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asExpirationUpdate().withExpiration(expiresInSeconds);
    List<Recipient> recipients = new ArrayList<>(1);
    recipients.add(recipient);
    List<SendMessageResult> result = sendMessage(messageBuilder, recipients);
    accountData.save();
    return result;
  }

  private List<SendMessageResult> sendGroupInfoRequest(byte[] groupId, Recipient recipient) throws IOException, SQLException {
    if (groupId == null) {
      return null;
    }

    SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO).withId(groupId);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

    // Send group info request message to the recipient who sent us a message with this groupId
    final List<Recipient> membersSend = new ArrayList<>();
    membersSend.add(recipient);
    return sendMessage(messageBuilder, membersSend);
  }

  public ContactStore.ContactInfo updateContact(ContactStore.ContactInfo contact) throws IOException, SQLException {
    if (contact.address.uuid == null) {
      Recipient recipient = new RecipientsTable(aci.uuid()).get(contact.address.number, null);
      contact.address = new JsonAddress(recipient.getAddress());
    }
    return accountData.contactStore.updateContact(contact);
  }

  public GroupInfo updateGroup(byte[] groupId, String name, List<String> stringMembers, String avatar)
      throws IOException, GroupNotFoundException, NotAGroupMemberException, SQLException {
    if (groupId.length == 0) {
      groupId = null;
    }
    if (name.isEmpty()) {
      name = null;
    }
    if (avatar.isEmpty()) {
      avatar = null;
    }
    List<Recipient> members = new ArrayList<>();
    for (String stringMember : stringMembers) {
      members.add(getRecipientsTable().get(stringMember));
    }
    return sendUpdateGroupMessage(groupId, name, members, avatar);
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

  public void requestSyncBlocked() throws IOException, org.whispersystems.signalservice.api.crypto.UntrustedIdentityException, SQLException {
    SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.BLOCKED).build();
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

  public SendMessageResult sendTypingMessage(SignalServiceTypingMessage message, Recipient recipient) throws IOException, SQLException {
    SignalServiceMessageSender messageSender = dependencies.getMessageSender();
    SignalServiceAddress address = recipient.getAddress();
    try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
      messageSender.sendTyping(address, getAccessPairFor(recipient), message);
      return null;
    } catch (org.whispersystems.signalservice.api.crypto.UntrustedIdentityException e) {
      account.getProtocolStore().handleUntrustedIdentityException(e);
      return SendMessageResult.identityFailure(address, e.getIdentityKey());
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

    try {
      ProfileAndCredentialEntry profile = getRecipientProfileKeyCredential(self);
      if (profile.getProfile() != null) {
        messageBuilder.withProfileKey(profile.getProfileKey().serialize());
      }
    } catch (InterruptedException | ExecutionException | TimeoutException | NoSuchAccountException | ServerNotFoundException | InvalidProxyException e) {
      logger.warn("Failed to get own profile key", e);
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
          try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
            result = messageSender.sendDataMessage(recipientAddresses, getAccessPairFor(recipients), isRecipientUpdate, ContentHint.DEFAULT, message,
                                                   SignalServiceMessageSender.LegacyGroupEvents.EMPTY,
                                                   sendResult -> logger.trace("Partial message send result: {}", sendResult.isSuccess()), () -> false);
          }
          for (SendMessageResult r : result) {
            if (r.getIdentityFailure() != null) {
              try {
                Recipient recipient = getRecipientsTable().get(r.getAddress());
                account.getProtocolStore().saveIdentity(recipient, r.getIdentityFailure().getIdentityKey(), Config.getNewKeyTrustLevel());
              } catch (SQLException e) {
                logger.error("error storing new identity", e);
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
        SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(self.getAddress()), message.getTimestamp(), message, message.getExpiresInSeconds(),
                                                                     Collections.singletonMap(self.getAddress(), unidentifiedAccess.isPresent()), false);
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
          ContactStore.ContactInfo contact = accountData.contactStore.getContact(recipient);
          messageBuilder.withExpiration(contact.messageExpirationTime);
          message = messageBuilder.build();
          try {
            if (self.equals(recipient)) { // sending to ourself
              final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessPairFor(recipient);
              SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(recipient.getAddress()), message.getTimestamp(), message, message.getExpiresInSeconds(),
                                                                           Collections.singletonMap(recipient.getAddress(), unidentifiedAccess.isPresent()), false);
              SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);
              try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
                messageSender.sendSyncMessage(syncMessage, unidentifiedAccess);
              }
              //              results.add(SendMessageResult.success(recipient, devices, false, unidentifiedAccess.isPresent(), true, (System.currentTimeMillis() - start),
              //              Optional.absent());
            } else {
              try (SignalSessionLock.Lock ignored = dependencies.getSessionLock().acquire()) {
                results.add(
                    messageSender.sendDataMessage(recipient.getAddress(), getAccessPairFor(recipient), ContentHint.DEFAULT, message, new IndividualSendEventsLogger(recipient)));
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
             ProtocolNoSessionException, ProtocolInvalidVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolDuplicateMessageException,
             UnsupportedDataMessageException, org.whispersystems.libsignal.UntrustedIdentityException, InvalidMessageStructureException, IOException, SQLException,
             InterruptedException {
    CertificateValidator certificateValidator = new CertificateValidator(unidentifiedSenderTrustRoot);
    SignalServiceCipher cipher = new SignalServiceCipher(self.getAddress(), account.getDeviceId(), account.getProtocolStore(), dependencies.getSessionLock(), certificateValidator);
    Semaphore sem = new Semaphore(1);
    int watchdogTime = Config.getDecryptionTimeout();
    if (watchdogTime > 0) {
      sem.acquire();
      Thread t = new Thread(() -> {
        // a watchdog thread that will make signald exit if decryption takes too long. This behavior is sub-optimal, but
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
        }
      });

      t.start();
    }

    Histogram.Timer timer = messageDecryptionTime.labels(account.getUUID().toString()).startTimer();
    try {
      return cipher.decrypt(envelope);
    } catch (ProtocolUntrustedIdentityException e) {
      if (e.getCause() instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
        org.whispersystems.libsignal.UntrustedIdentityException identityException = (org.whispersystems.libsignal.UntrustedIdentityException)e.getCause();
        account.getProtocolStore().saveIdentity(identityException.getName(), identityException.getUntrustedIdentity(), Config.getNewKeyTrustLevel());
        throw identityException;
      }
      throw e;
    } catch (SelfSendException e) {
      logger.debug("Dropping UD message from self (because that's what Signal Android does)");
      return null;
    } finally {
      if (watchdogTime > 0) {
        sem.release();
      }
      double duration = timer.observeDuration();
      logger.debug("message decrypted in " + duration + " seconds");
    }
  }

  private void handleEndSession(Recipient address) { account.getProtocolStore().deleteAllSessions(address); }

  public List<SendMessageResult> send(SignalServiceDataMessage.Builder message, Recipient recipient, GroupIdentifier recipientGroupId, List<Recipient> members)
      throws IOException, InvalidRecipientException, UnknownGroupException, SQLException, NoSendPermissionException, InvalidInputException {
    if (recipientGroupId != null && recipient == null) {
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

      return sendGroupV2Message(message, group.getSignalServiceGroupV2(), members);
    } else if (recipient != null && recipientGroupId == null) {
      List<Recipient> r = new ArrayList<>();
      r.add(recipient);
      return sendMessage(message, r);
    } else {
      throw new InvalidRecipientException();
    }
  }

  public SignalServiceMessageReceiver getMessageReceiver() { return dependencies.getMessageReceiver(); }

  public SignalServiceMessageSender getMessageSender() { return dependencies.getMessageSender(); }

  public interface ReceiveMessageHandler {
    void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
  }

  private List<Job> handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, Recipient source, Recipient destination, boolean ignoreAttachments)
      throws MissingConfigurationException, IOException, VerificationFailedException, SQLException, InvalidInputException {

    List<Job> jobs = new ArrayList<>();
    if (message.getGroupContext().isPresent()) {
      SignalServiceGroup groupInfo;
      SignalServiceGroupContext groupContext = message.getGroupContext().get();

      if (groupContext.getGroupV2().isPresent()) {
        SignalServiceGroupV2 group = message.getGroupContext().get().getGroupV2().get();
        Optional<GroupsTable.Group> localState = account.getGroupsTable().get(group);

        if (!localState.isPresent() || localState.get().getRevision() < group.getRevision()) {
          GroupSecretParams groupSecretParams = GroupSecretParams.deriveFromMasterKey(group.getMasterKey());
          try {
            account.getGroups().getGroup(groupSecretParams, group.getRevision());
          } catch (InvalidGroupStateException | InvalidProxyException | NoSuchAccountException | ServerNotFoundException e) {
            logger.warn("error fetching state of incoming group", e);
          }
        }
      }

      if (groupContext.getGroupV1().isPresent()) {
        logger.warn("v1 group support is being removed https://gitlab.com/signald/signald/-/issues/224");
        groupInfo = groupContext.getGroupV1().get();
        GroupInfo group = accountData.groupStore.getGroup(groupInfo.getGroupId());

        if (message.isExpirationUpdate()) {
          if (group.messageExpirationTime != message.getExpiresInSeconds()) {
            group.messageExpirationTime = message.getExpiresInSeconds();
          }
          accountData.groupStore.updateGroup(group);
          accountData.save();
        }

        switch (groupInfo.getType()) {
        case UPDATE:
          if (group == null) {
            group = new GroupInfo(groupInfo.getGroupId());
          }

          if (groupInfo.getAvatar().isPresent()) {
            SignalServiceAttachment avatar = groupInfo.getAvatar().get();
            if (avatar.isPointer()) {
              try {
                retrieveGroupAvatarAttachment(avatar.asPointer(), group.groupId);
              } catch (IOException | InvalidMessageException e) {
                logger.warn("Failed to retrieve group avatar (" + avatar.asPointer().getRemoteId() + "): " + e.getMessage());
              }
            }
          }

          if (groupInfo.getName().isPresent()) {
            group.name = groupInfo.getName().get();
          }

          if (groupInfo.getMembers().isPresent()) {
            List<SignalServiceAddress> members = getRecipientsTable().get(groupInfo.getMembers().get()).stream().map(Recipient::getAddress).collect(Collectors.toList());
            group.addMembers(members);
          }

          accountData.groupStore.updateGroup(group);
          break;
        case DELIVER:
          if (group == null) {
            try {
              sendGroupInfoRequest(groupInfo.getGroupId(), source);
            } catch (IOException e) {
              logger.catching(e);
            }
          }
          break;
        case QUIT:
          if (group == null) {
            try {
              sendGroupInfoRequest(groupInfo.getGroupId(), source);
            } catch (IOException e) {
              logger.catching(e);
            }
          } else {
            group.removeMember(source);
            accountData.groupStore.updateGroup(group);
          }
          break;
        case REQUEST_INFO:
          if (group != null) {
            jobs.add(new SendLegacyGroupUpdateJob(this, groupInfo.getGroupId(), source));
          }
          break;
        }
      }
    } else {
      ContactStore.ContactInfo c = accountData.contactStore.getContact(isSync ? destination : source);
      if (c.messageExpirationTime != message.getExpiresInSeconds()) {
        c.messageExpirationTime = message.getExpiresInSeconds();
        accountData.contactStore.updateContact(c);
        accountData.save();
      }
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

    if (message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
      final ProfileKey profileKey;
      try {
        profileKey = new ProfileKey(message.getProfileKey().get());
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
      ProfileAndCredentialEntry entry = accountData.profileCredentialStore.storeProfileKey(source, profileKey);
      RefreshProfileJob j = new RefreshProfileJob(this, entry);
      if (j.needsRefresh()) {
        jobs.add(j);
      }
    }

    if (message.getSticker().isPresent()) {
      DownloadStickerJob job = new DownloadStickerJob(this, message.getSticker().get());
      if (job.needsDownload()) {
        try {
          job.run();
        } catch (NoSuchAccountException | InvalidMessageException e) {
          logger.error("Sticker failed to download");
        }
      }
    }

    return jobs;
  }

  public void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments)
      throws IOException, MissingConfigurationException, SQLException, InvalidInputException {
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
            } catch (VerificationFailedException e) {
              logger.catching(e);
            }
          }
        }
        if (exception == null && content != null) {
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
      StoredEnvelope storedEnvelope = accountData.getDatabase().getMessageQueueTable().nextEnvelope();
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
            } catch (VerificationFailedException e) {
              logger.catching(e);
            }
          }
        }
        if (exception == null && content != null) {
          handler.handleMessage(envelope, content, exception);
        }
      } finally {
        accountData.getDatabase().getMessageQueueTable().deleteEnvelope(storedEnvelope.databaseId);
      }
    }
  }

  public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler)
      throws IOException, MissingConfigurationException, VerificationFailedException, SQLException, InvalidInputException {
    retryFailedReceivedMessages(handler, ignoreAttachments);
    accountData.saveIfNeeded();

    SignalWebSocket websocket = dependencies.getWebSocket();

    logger.debug("connecting to websocket");
    websocket.connect();

    MessageQueueTable messageQueueTable = new Database(aci.uuid()).getMessageQueueTable();

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
              logger.warn("Failed to store encrypted message in sqlite cache, ignoring: " + e.getMessage());
            }
          });
          if (result.isPresent()) {
            envelope = result.get();
          } else {
            continue;
          }
        } catch (TimeoutException e) {
          if (returnOnTimeout)
            return;
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
            handleMessage(envelope, content, ignoreAttachments);
          }
        }
        if (exception == null && content != null) {
          handler.handleMessage(envelope, content, exception);
        }
        try {
          Long id = databaseId.getValue();
          if (id != null) {
            messageQueueTable.deleteEnvelope(id);
          }
        } catch (SQLException e) {
          logger.error("failed to remove cached message from database");
        }
      }
    } finally {
      logger.debug("disconnecting websocket");
      websocket.disconnect();
    }
  }

  private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments)
      throws IOException, MissingConfigurationException, VerificationFailedException, SQLException, InvalidInputException {
    List<Job> jobs = new ArrayList<>();
    if (content == null) {
      return;
    }

    RecipientsTable recipientsTable = getRecipientsTable();
    Recipient source = recipientsTable.get((envelope.isUnidentifiedSender() && envelope.hasSourceUuid()) ? envelope.getSourceAddress() : content.getSender());
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
          logger.debug("Renewing the session with sender");
          jobs.add(new ResetSessionJob(account, source));
        }
      } else {
        logger.debug("Reset shared sender keys with this recipient");
        account.getSenderKeysSharedWith().deleteSharedWith(source);
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
        SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();

        Recipient sendMessageRecipient = null;
        if (syncMessage.getSent().get().getDestination().isPresent()) {
          sendMessageRecipient = recipientsTable.get(syncMessage.getSent().get().getDestination().get());
        }
        jobs.addAll(handleSignalServiceDataMessage(message, true, source, sendMessageRecipient, ignoreAttachments));
      }

      if (syncMessage.getRequest().isPresent() && account.getDeviceId() == SignalServiceAddress.DEFAULT_DEVICE_ID) {
        RequestMessage rm = syncMessage.getRequest().get();
        if (rm.isContactsRequest()) {
          jobs.add(new SendContactsSyncJob(this));
        }
        if (rm.isGroupsRequest()) {
          jobs.add(new SendGroupSyncJob(this));
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
              accountData.contactStore.clear();
            }
            DeviceContact c;
            while ((c = s.read()) != null) {
              Recipient recipient = recipientsTable.get(c.getAddress());
              ContactStore.ContactInfo contact = accountData.contactStore.getContact(recipient);
              contact.update(c);
              updateContact(contact);
              if (c.getAvatar().isPresent()) {
                retrieveContactAvatarAttachment(c.getAvatar().get(), recipient);
              }
              if (c.getProfileKey().isPresent()) {
                accountData.profileCredentialStore.storeProfileKey(recipient, c.getProfileKey().get());
              }
            }
            accountData.save();
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
        final VerifiedMessage verifiedMessage = syncMessage.getVerified().get();
        Recipient destination = getRecipientsTable().get(verifiedMessage.getDestination());
        TrustLevel trustLevel = TrustLevel.fromVerifiedState(verifiedMessage.getVerified());
        account.getProtocolStore().saveIdentity(destination, verifiedMessage.getIdentityKey(), trustLevel);
        logger.info("received verified state update from device " + content.getSenderDevice());
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
      Optional<SignalServiceAddress> sourceAddress = sourceACI == null && source.isEmpty() ? Optional.absent() : Optional.of(new SignalServiceAddress(sourceACI, source));
      return new SignalServiceEnvelope(type, sourceAddress, sourceDevice, timestamp, legacyMessage, content, serverReceivedTimestamp, serverDeliveredTimestamp, uuid);
    }
  }

  public File getContactAvatarFile(Recipient recipient) { return new File(avatarsPath, "contact-" + recipient.getAddress().getNumber().get()); }

  public File getProfileAvatarFile(Recipient recipient) {
    if (recipient.getUUID() == null) {
      return null;
    }
    return new File(avatarsPath, recipient.getUUID().toString());
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

  public File getGroupAvatarFile(GroupIdentifier group) { return getGroupAvatarFile(group.serialize()); }

  public File getGroupAvatarFile(byte[] groupId) { return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_")); }

  private File retrieveGroupAvatarAttachment(SignalServiceAttachment attachment, byte[] groupId) throws IOException, InvalidMessageException, MissingConfigurationException {
    createPrivateDirectories(avatarsPath);
    if (attachment.isPointer()) {
      SignalServiceAttachmentPointer pointer = attachment.asPointer();
      return retrieveAttachment(pointer, getGroupAvatarFile(groupId), false);
    } else {
      SignalServiceAttachmentStream stream = attachment.asStream();
      return retrieveAttachment(stream, getGroupAvatarFile(groupId));
    }
  }

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

  public List<ContactStore.ContactInfo> getContacts() {
    if (accountData.contactStore == null) {
      return Collections.emptyList();
    }
    return this.accountData.contactStore.getContacts();
  }

  public GroupInfo getGroup(byte[] groupId) { return accountData.groupStore.getGroup(groupId); }

  public List<IdentityKeysTable.IdentityKeyRow> getIdentities() throws SQLException, InvalidKeyException { return account.getProtocolStore().getIdentities(); }

  public List<IdentityKeysTable.IdentityKeyRow> getIdentities(Recipient recipient) throws SQLException, InvalidKeyException {
    return account.getProtocolStore().getIdentities(recipient);
  }

  public boolean trustIdentity(Recipient recipient, byte[] fingerprint, TrustLevel level) throws SQLException, InvalidKeyException {
    List<IdentityKeysTable.IdentityKeyRow> ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (IdentityKeysTable.IdentityKeyRow id : ids) {
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
    List<IdentityKeysTable.IdentityKeyRow> ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (IdentityKeysTable.IdentityKeyRow id : ids) {
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
    List<IdentityKeysTable.IdentityKeyRow> ids = account.getProtocolStore().getIdentities(recipient);
    if (ids == null) {
      return false;
    }
    for (IdentityKeysTable.IdentityKeyRow id : ids) {
      Fingerprint fingerprint = SafetyNumberHelper.computeFingerprint(self, getIdentity(), recipient, id.getKey());
      assert fingerprint != null;
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

  public List<Optional<UnidentifiedAccessPair>> getAccessPairFor(Collection<Recipient> recipients) {
    List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
    for (Recipient recipient : recipients) {
      result.add(getAccessPairFor(recipient));
    }
    return result;
  }

  public Optional<UnidentifiedAccessPair> getAccessPairFor(Recipient recipient) {
    ProfileAndCredentialEntry recipientProfileKeyCredential = accountData.profileCredentialStore.get(recipient);
    if (recipientProfileKeyCredential == null) {
      return Optional.absent();
    }

    byte[] recipientUnidentifiedAccessKey = recipientProfileKeyCredential.getUnidentifiedAccessKey();

    ProfileKey selfProfileKey;
    try {
      selfProfileKey = accountData.getProfileKey();
    } catch (InvalidInputException e) {
      logger.warn("unexpected error while getting own profile key: " + e);
      return Optional.absent();
    }

    byte[] selfUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(selfProfileKey);
    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

    if (selfUnidentifiedAccessKey == null) {
      logger.debug("cannot get unidentified access: no unidentified access key for own account");
      return Optional.absent();
    }

    if (selfUnidentifiedAccessCertificate == null) {
      logger.debug("cannot get unidentified access: no unidentified access certificate for own account");
      return Optional.absent();
    }

    if (recipientUnidentifiedAccessKey == null) {
      logger.debug("cannot get unidentified access: no unidentified access key for recipient");
      return Optional.absent();
    }

    try {
      return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(recipientUnidentifiedAccessKey, selfUnidentifiedAccessCertificate),
                                                    new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate)));
    } catch (InvalidCertificateException e) {
      logger.debug("cannot get unidentififed access: ", e);
      return Optional.absent();
    }
  }

  private byte[] getSenderCertificate() {
    try {
      long lastRefresh = account.getLastSenderCertificateRefreshTime();
      byte[] cert;
      if (System.currentTimeMillis() - lastRefresh > TimeUnit.DAYS.toMillis(1)) {
        logger.debug("refreshing unidentified access sender certificate");
        cert = dependencies.getAccountManager().getSenderCertificateForPhoneNumberPrivacy();
        account.setSenderCertificate(cert);
        account.setSenderCertificateRefreshTimeNow();
      } else {
        cert = account.getSenderCertificate();
      }
      return cert;
    } catch (IOException | SQLException e) {
      logger.warn("Failed to get sealed sender certificate, ignoring: {}", e.getMessage());
      return null;
    }
  }

  public void setProfile(String name, File avatar) throws IOException, InvalidInputException {
    try (final StreamDetails streamDetails = avatar == null ? null : AttachmentUtil.createStreamDetailsFromFile(avatar)) {
      dependencies.getAccountManager().setVersionedProfile(self.getACI(), accountData.getProfileKey(), name, "", "", Optional.absent(), streamDetails, new ArrayList<>());
    }
  }

  public SignalServiceProfile getSignalServiceProfile(Recipient recipient, ProfileKey profileKey) throws InterruptedException, ExecutionException, TimeoutException {
    final SignalServiceMessageReceiver messageReceiver = dependencies.getMessageReceiver();
    ListenableFuture<ProfileAndCredential> profile =
        messageReceiver.retrieveProfile(recipient.getAddress(), Optional.of(profileKey), getUnidentifiedAccess(), SignalServiceProfile.RequestType.PROFILE, Locale.getDefault());
    return profile.get(10, TimeUnit.SECONDS).getProfile();
  }

  public RecipientsTable getRecipientsTable() { return new RecipientsTable(aci.uuid()); }

  public void refreshAccount() throws IOException, SQLException {
    String deviceName = account.getDeviceName();
    if (deviceName == null) {
      deviceName = "signald";
      account.setDeviceName(deviceName);
    }
    deviceName = DeviceNameUtil.encryptDeviceName(deviceName, account.getProtocolStore().getIdentityKeyPair().getPrivateKey());
    int localRegistrationId = account.getLocalRegistrationId();
    dependencies.getAccountManager().setAccountAttributes(deviceName, null, localRegistrationId, true, null, null, accountData.getSelfUnidentifiedAccessKey(), true,
                                                          ServiceConfig.CAPABILITIES, true);
    account.setLastAccountRefresh(ACCOUNT_REFRESH_VERSION);
  }

  private void refreshAccountIfNeeded() throws IOException, SQLException {
    if (account.getLastAccountRefresh() < ACCOUNT_REFRESH_VERSION) {
      refreshAccount();
    }
  }

  public AccountData getAccountData() { return accountData; }

  public ProfileAndCredentialEntry getRecipientProfileKeyCredential(Recipient recipient)
      throws InterruptedException, ExecutionException, TimeoutException, IOException, SQLException, NoSuchAccountException, ServerNotFoundException, InvalidProxyException {
    ProfileAndCredentialEntry profileEntry = accountData.profileCredentialStore.get(recipient);
    if (profileEntry == null) {
      return null;
    }
    RefreshProfileJob action = new RefreshProfileJob(this, profileEntry);
    if (action.needsRefresh()) {
      action.run();
      return accountData.profileCredentialStore.get(recipient);
    } else {
      return profileEntry;
    }
  }

  public SignalProfile decryptProfile(final Recipient recipient, final ProfileKey profileKey, final SignalServiceProfile encryptedProfile) throws IOException {
    File localAvatarPath = null;
    if (recipient.getACI() != null) {
      localAvatarPath = getProfileAvatarFile(recipient);
      if (encryptedProfile.getAvatar() != null) {
        createPrivateDirectories(avatarsPath);
        try (OutputStream outputStream = new FileOutputStream(localAvatarPath)) {
          retrieveProfileAvatar(encryptedProfile.getAvatar(), profileKey, outputStream);
        } catch (IOException e) {
          logger.info("Failed to retrieve profile avatar, ignoring: " + e.getMessage());
        }
      }
    }

    ProfileCipher profileCipher = new ProfileCipher(profileKey);

    String name;
    try {
      name = encryptedProfile.getName() == null ? null : profileCipher.decryptString(Base64.decode(encryptedProfile.getName()));
    } catch (InvalidCiphertextException e) {
      name = null;
      logger.debug("error decrypting profile name.", e);
    }

    String about;
    try {
      about = encryptedProfile.getAbout() == null ? null : profileCipher.decryptString(Base64.decode(encryptedProfile.getAbout()));
    } catch (InvalidCiphertextException e) {
      about = null;
      logger.debug("error decrypting profile about text.", e);
    }

    String aboutEmoji;
    try {
      aboutEmoji = encryptedProfile.getAboutEmoji() == null ? null : profileCipher.decryptString(Base64.decode(encryptedProfile.getAboutEmoji()));
    } catch (InvalidCiphertextException e) {
      aboutEmoji = null;
      logger.debug("error decrypting profile emoji.", e);
    }

    String unidentifiedAccess;
    try {
      unidentifiedAccess = encryptedProfile.getUnidentifiedAccess() == null || !profileCipher.verifyUnidentifiedAccess(Base64.decode(encryptedProfile.getUnidentifiedAccess()))
                               ? null
                               : encryptedProfile.getUnidentifiedAccess();
    } catch (IOException e) {
      unidentifiedAccess = null;
    }

    SignalServiceProtos.PaymentAddress paymentAddress = null;
    byte[] encryptedPaymentsAddress = encryptedProfile.getPaymentAddress();
    if (encryptedPaymentsAddress != null) {
      try {
        byte[] decrypted = profileCipher.decryptWithLength(encryptedPaymentsAddress);
        paymentAddress = SignalServiceProtos.PaymentAddress.parseFrom(decrypted);
      } catch (InvalidCiphertextException ignored) {
      }
    }

    List<SignalServiceProfile.Badge> badges = encryptedProfile.getBadges();

    return new SignalProfile(encryptedProfile, name, about, aboutEmoji, localAvatarPath, unidentifiedAccess, paymentAddress, badges);
  }

  private void retrieveProfileAvatar(String avatarsPath, ProfileKey profileKey, OutputStream outputStream) throws IOException {
    File tmpFile = Util.createTempFile();
    try (InputStream input = dependencies.getMessageReceiver().retrieveProfileAvatar(avatarsPath, tmpFile, profileKey, ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE)) {
      Util.copyStream(input, outputStream, (int)ServiceConfig.AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE);
    } finally {
      try {
        Files.delete(tmpFile.toPath());
      } catch (IOException e) {
        logger.warn("Failed to delete received profile avatar temp file “{}”, ignoring: {}", tmpFile, e.getMessage());
      }
    }
  }

  public void deleteAccount(boolean remote) throws IOException, SQLException {
    accountData.markForDeletion();
    if (remote) {
      dependencies.getAccountManager().deleteAccount();
    }
    SignalDependencies.delete(aci);
    accountData.delete();
    synchronized (managers) { managers.remove(aci.toString()); }
    logger.info("deleted all local account data");
  }

  public Optional<UnidentifiedAccess> getUnidentifiedAccess() {
    byte[] selfUnidentifiedAccessKey;
    try {
      selfUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(accountData.getProfileKey());
    } catch (InvalidInputException e) {
      return Optional.absent();
    }
    byte[] selfUnidentifiedAccessCertificate = getSenderCertificate();

    try {
      return Optional.of(new UnidentifiedAccess(selfUnidentifiedAccessKey, selfUnidentifiedAccessCertificate));
    } catch (InvalidCertificateException e) {
      return Optional.absent();
    }
  }

  public SignalServiceConfiguration getServiceConfiguration() { return serviceConfiguration; }
}
