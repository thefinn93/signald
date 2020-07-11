/*
 * Copyright (C) 2020 Finn Herzfeld
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
package io.finn.signald;

import io.finn.signald.clientprotocol.v1.JsonAddress;
import io.finn.signald.exceptions.InvalidRecipientException;
import io.finn.signald.storage.*;
import io.finn.signald.util.SafetyNumberHelper;
import okhttp3.Interceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.TrustLevel;
import org.signal.libsignal.metadata.*;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;
import org.whispersystems.libsignal.util.Medium;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessagePipe;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.configuration.*;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.util.Base64;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.file.attribute.PosixFilePermission.*;
import static org.whispersystems.signalservice.internal.util.Util.isEmpty;

class Manager {
    private final Logger logger;
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();
    private final static SignalServiceConfiguration serviceConfiguration = Manager.generateSignalServiceConfiguration();
    private final static String USER_AGENT = BuildConfig.USER_AGENT;
    private static final SignalServiceProfile.Capabilities SERVICE_CAPABILITIES = new SignalServiceProfile.Capabilities(true, false, false);

    private final static int PREKEY_MINIMUM_COUNT = 20;
    private final static int PREKEY_BATCH_SIZE = 100;
    private final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;

    private static ConcurrentHashMap<String,Manager> managers = new ConcurrentHashMap<>();

    private static String settingsPath;
    private static String dataPath;
    private static String attachmentsPath;
    private static String avatarsPath;

    private AccountData accountData;

    private SignalServiceAccountManager accountManager;
    private SignalServiceMessagePipe messagePipe = null;
    private SignalServiceMessagePipe unidentifiedMessagePipe = null;

    private UptimeSleepTimer sleepTimer = new UptimeSleepTimer();

    static SignalServiceConfiguration generateSignalServiceConfiguration() {
        final Interceptor userAgentInterceptor = chain ->
                chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build());

        Map<Integer, SignalCdnUrl[]> signalCdnUrlMap = new HashMap<>();
        signalCdnUrlMap.put(0, new SignalCdnUrl[]{new SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, TRUST_STORE)});
        // unclear why there is no CDN 1
        signalCdnUrlMap.put(2, new SignalCdnUrl[]{new SignalCdnUrl(BuildConfig.SIGNAL_CDN2_URL, TRUST_STORE)});

        try {
            return new SignalServiceConfiguration(
                    new SignalServiceUrl[]{new SignalServiceUrl(BuildConfig.SIGNAL_URL, TRUST_STORE)},
                    signalCdnUrlMap,
                    new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl(BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL, TRUST_STORE)},
                    new SignalKeyBackupServiceUrl[]{new SignalKeyBackupServiceUrl(BuildConfig.SIGNAL_KEY_BACKUP_URL, TRUST_STORE)},
                    new SignalStorageUrl[]{new SignalStorageUrl(BuildConfig.SIGNAL_STORAGE_URL, TRUST_STORE)},
                    Collections.singletonList(userAgentInterceptor),
                    Optional.absent(),
                    Base64.decode(BuildConfig.SIGNAL_ZK_GROUP_SERVER_PUBLIC_PARAMS_HEX)
            );
        } catch (IOException e) {
            LogManager.getLogger("manager").catching(e);
            throw new AssertionError(e);
        }
    }

    public static Manager get(String username) throws IOException, NoSuchAccountException {
        return get(username, false);
    }

    public static Manager get(String username, boolean newUser) throws IOException, NoSuchAccountException {
        Logger logger = LogManager.getLogger("manager");
        if(managers.containsKey(username)) {
            return managers.get(username);
        }

        managers.put(username, new Manager(username));
        Manager m = managers.get(username);
        if(!newUser) {
            try {
                if (m.userExists()) {
                    m.init();
                } else {
                    throw new NoSuchAccountException(username);
                }
            } catch(Exception e) {
                managers.remove(username);
                throw e;
            }
        }
        logger.info("Created a manager for " + Util.redact(username));
        return m;
    }

    public static List<Manager> getAll() {
        Logger logger = LogManager.getLogger("manager");
        // We have to create a manager for each account that we're listing, which is all of them :/
        List<Manager> allManagers = new LinkedList<>();
        File[] allAccounts = new File(dataPath).listFiles();
        if(allAccounts == null) {
            return allManagers;
        }
        for(File account : allAccounts) {
            if(!account.isDirectory()) {
                try {
                    allManagers.add(Manager.get(account.getName()));
                } catch (IOException | NoSuchAccountException e) {
                    logger.warn("Failed to load account from file: " + account.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }
        return allManagers;
    }

    public Manager(String username) {
        logger =  LogManager.getLogger("manager-" + Util.redact(username));
        logger.info("Creating new manager for " + Util.redact(username) + " (stored at " + settingsPath + ")");
        accountData = new AccountData();
        accountData.username = username;
//        jsonProcessor.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE); // disable autodetect
//        jsonProcessor.enable(SerializationFeature.INDENT_OUTPUT); // for pretty print, you can disable it.
//        jsonProcessor.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
//        jsonProcessor.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
//        jsonProcessor.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
//        jsonProcessor.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    }

    public Manager(AccountData account) {
        accountData = account;
        logger =  LogManager.getLogger("manager-" + Util.redact(accountData.username));
        logger.info("Creating new manager for " + Util.redact(accountData.username) + " (stored at " + settingsPath + ")");
    }

    public static void setDataPath(String path) {
        settingsPath = path;
        dataPath = settingsPath + "/data";
        attachmentsPath = settingsPath + "/attachments";
        avatarsPath = settingsPath + "/avatars";
    }

    public String getUsername() {
        return accountData.username;
    }

    public SignalServiceAddress getOwnAddress() {
        return accountData.address.getSignalServiceAddress();
    }

    public IdentityKey getIdentity() {
        return accountData.axolotlStore.identityKeyStore.getIdentityKeyPair().getPublicKey();
    }

    public int getDeviceId() {
        return accountData.deviceId;
    }

    public String getFileName() {
        return Manager.getFileName(accountData.username);
    }

    public static String getFileName(String username) {
        return dataPath + "/" + username;
    }

    private String getMessageCachePath() {
        return dataPath + "/" + accountData.username + ".d/msg-cache";
    }

    private String getMessageCachePath(String sender) {
        return getMessageCachePath() + "/" + sender.replace("/", "_");
    }

    private File getMessageCacheFile(SignalServiceEnvelope envelope, long now) throws IOException {
        String source = envelope.getSourceE164().isPresent() ? envelope.getSourceE164().get() : "";
        String cachePath = getMessageCachePath(source);
        createPrivateDirectories(cachePath);
        return new File(cachePath + "/" + now + "_" + envelope.getTimestamp());
    }

    private static void createPrivateDirectories(String path) throws IOException {
        final Path file = new File(path).toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE);
            Files.createDirectories(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(file);
        }
    }

    public boolean userExists() {
        if (accountData.username == null) {
            return false;
        }
        File f = new File(getFileName());
        return !(!f.exists() || f.isDirectory());
    }

    public static boolean userExists(String username) {
        if (username == null) {
            return false;
        }
        File f = new File(Manager.getFileName(username));
        return !(!f.exists() || f.isDirectory());
    }

    public boolean userHasKeys() {
        return accountData.axolotlStore != null;
    }

    public void init() throws IOException {
        accountData = AccountData.load(new File(getFileName()));
        accountManager = getAccountManager();
        if(accountData.address.uuid == null) {
            accountData.address.uuid = accountManager.getOwnUuid().toString();
            accountData.save();
        }
        try {
            if (accountData.registered && accountManager.getPreKeysCount() < PREKEY_MINIMUM_COUNT) {
                refreshPreKeys();
                accountData.save();
            }
        } catch (AuthorizationFailedException e) {
            logger.warn("Authorization failed, was the number registered elsewhere?");
            accountData.registered = false;
        }
    }

    public void createNewIdentity() {
        IdentityKeyPair identityKey = KeyHelper.generateIdentityKeyPair();
        int registrationId = KeyHelper.generateRegistrationId(false);
        accountData.axolotlStore = new SignalProtocolStore(identityKey, registrationId);
        accountData.registered = false;
        logger.info("Generating new identity pair");
    }

    public boolean isRegistered() {
        return accountData.registered;
    }

    public void register(boolean voiceVerification, Optional<String> captcha) throws IOException, InvalidInputException {
        accountData.password = Util.getSecret(18);

        accountManager = getAccountManager();

        if (voiceVerification) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), captcha, Optional.absent());  // TODO: Allow requester to set the locale and challenge
        } else {
            accountManager.requestSmsVerificationCode(false, captcha, Optional.absent()); //  TODO: Allow requester to set challenge and androidSmsReceiverSupported
        }

        accountData.registered = false;
        accountData.init();
        accountData.save();
    }

    private SignalServiceAccountManager getAccountManager() {
        return new SignalServiceAccountManager(serviceConfiguration, accountData.getUUID(), accountData.username, accountData.password, accountData.deviceId, BuildConfig.SIGNAL_AGENT, sleepTimer);
    }

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String name = null;
            try {
                name = URLDecoder.decode(param.split("=")[0], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            String value = null;
            try {
                value = URLDecoder.decode(param.split("=")[1], "utf-8");
            } catch (UnsupportedEncodingException e) {
                // Impossible
            }
            map.put(name, value);
        }
        return map;
    }

    public void addDeviceLink(URI linkUri) throws IOException, InvalidKeyException {
        Map<String, String> query = getQueryMap(linkUri.getRawQuery());
        String deviceIdentifier = query.get("uuid");
        String publicKeyEncoded = query.get("pub_key");

        if (isEmpty(deviceIdentifier) || isEmpty(publicKeyEncoded)) {
            throw new RuntimeException("Invalid device link uri");
        }

        ECPublicKey deviceKey = Curve.decodePoint(Base64.decode(publicKeyEncoded), 0);

        addDevice(deviceIdentifier, deviceKey);
    }

    private void addDevice(String deviceIdentifier, ECPublicKey deviceKey) throws IOException, InvalidKeyException {
        IdentityKeyPair identityKeyPair = accountData.axolotlStore.identityKeyStore.getIdentityKeyPair();
        String verificationCode = accountManager.getNewDeviceVerificationCode();

        // TODO send profile key
        accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, Optional.absent(), verificationCode);
    }

    private List<PreKeyRecord> generatePreKeys() throws IOException {
        List<PreKeyRecord> records = new LinkedList<>();

        for (int i = 0; i < PREKEY_BATCH_SIZE; i++) {
            int preKeyId = (accountData.preKeyIdOffset + i) % Medium.MAX_VALUE;
            ECKeyPair keyPair = Curve.generateKeyPair();
            PreKeyRecord record = new PreKeyRecord(preKeyId, keyPair);

            accountData.axolotlStore.preKeys.storePreKey(preKeyId, record);
            records.add(record);
        }

        accountData.preKeyIdOffset = (accountData.preKeyIdOffset + PREKEY_BATCH_SIZE + 1) % Medium.MAX_VALUE;
        accountData.save();

        return records;
    }

    private SignedPreKeyRecord generateSignedPreKey(IdentityKeyPair identityKeyPair) throws IOException {
        try {
            ECKeyPair keyPair = Curve.generateKeyPair();
            byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), keyPair.getPublicKey().serialize());
            SignedPreKeyRecord record = new SignedPreKeyRecord(accountData.nextSignedPreKeyId, System.currentTimeMillis(), keyPair, signature);

            accountData.axolotlStore.storeSignedPreKey(accountData.nextSignedPreKeyId, record);
            accountData.nextSignedPreKeyId = (accountData.nextSignedPreKeyId + 1) % Medium.MAX_VALUE;
            accountData.save();

            return record;
        } catch (InvalidKeyException e) {
            throw new AssertionError(e);
        }
    }

    public void verifyAccount(String verificationCode) throws IOException, InvalidInputException {
        verificationCode = verificationCode.replace("-", "");
        accountData.signalingKey = Util.getSecret(52);
        VerifyAccountResponse response = accountManager.verifyAccountWithCode(verificationCode, accountData.signalingKey, accountData.axolotlStore.getLocalRegistrationId(), true, null, null, accountData.getSelfUnidentifiedAccessKey(), false, SERVICE_CAPABILITIES);

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        accountData.registered = true;

        refreshPreKeys();
        accountData.init();
        accountData.save();
    }

    void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(accountData.axolotlStore.getIdentityKeyPair());

        accountManager.setPreKeys(accountData.axolotlStore.getIdentityKeyPair().getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }

    private static SignalServiceAttachmentStream createAttachment(File attachmentFile) throws IOException {
        return createAttachment(attachmentFile, Optional.absent());
    }

    private static SignalServiceAttachmentStream createAttachment(File attachmentFile, Optional<String> caption) throws IOException {
        InputStream attachmentStream = new FileInputStream(attachmentFile);
        final long attachmentSize = attachmentFile.length();
        String mime = Files.probeContentType(attachmentFile.toPath());
        if (mime == null) {
            mime = "application/octet-stream";
        }
        // TODO mabybe add a parameter to set the voiceNote, preview, and caption option
        return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, Optional.absent(), 0, 0, System.currentTimeMillis(), caption, Optional.absent(), null, null, Optional.absent());
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
        File file = getGroupAvatarFile(groupId);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(createAttachment(file));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(SignalServiceAddress address) throws IOException {
        File file = getContactAvatarFile(address);
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

        if(!g.isMember(accountData.address)) {
            throw new NotAGroupMemberException(groupId, g.name);
        }

        return g;
    }

    public List<GroupInfo> getGroups() {
        return accountData.groupStore.getGroups();
    }

    public List<SendMessageResult> sendGroupMessage(SignalServiceDataMessage.Builder message, byte[] groupId) throws IOException, GroupNotFoundException, NotAGroupMemberException {
        if(groupId == null) {
            throw new AssertionError("Cannot send group message to null group ID");
        }
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER).withId(groupId).build();
        message.asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);

        // Don't send group message to ourself
        final List<SignalServiceAddress> membersSend = g.getMembers();
        membersSend.remove(accountData.address.getSignalServiceAddress());
        return sendMessage(message, membersSend);
    }

    public List<SendMessageResult> sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, NotAGroupMemberException {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);
        g.members.remove(accountData.address);
        accountData.groupStore.updateGroup(g);

        return sendMessage(messageBuilder, g.getMembers());
    }

    private static String join(CharSequence separator, Iterable<? extends CharSequence> list) {
        StringBuilder buf = new StringBuilder();
        for (CharSequence str : list) {
            if (buf.length() > 0) {
                buf.append(separator);
            }
            buf.append(str);
        }

        return buf.toString();
    }

    public byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
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
            Set<String> newMembers = new HashSet<>();
            for (String member : members) {
                try {
                    member = canonicalizeNumber(member);
                } catch (InvalidNumberException e) {
                    logger.warn("Failed to add member \"" + Util.redact(member) + "\" to group: " + e.getMessage());
                }
                if (g.members.contains(new JsonAddress(member))) {
                    continue;
                }
                newMembers.add(member);
                g.addMember(new JsonAddress(member));
            }
            final List<ContactTokenDetails> contacts = accountManager.getContacts(newMembers);
            if (contacts.size() != newMembers.size()) {
                // Some of the new members are not registered on Signal
                for (ContactTokenDetails contact : contacts) {
                    newMembers.remove(contact.getNumber());
                }
//                logger.warn("Failed to add members " + join(", ", newMembers) + " to group: Not registered on Signal");
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
        final List<SignalServiceAddress> membersSend = g.getMembers();
        membersSend.remove(accountData.address.getSignalServiceAddress());
        sendMessage(messageBuilder, membersSend);
        return g.groupId;
    }

    private List<SendMessageResult> sendUpdateGroupMessage(byte[] groupId, SignalServiceAddress recipient) throws IOException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException {
        if (groupId == null) {
            return null;
        }
        GroupInfo g = getGroupForSending(groupId);

        if (!g.members.contains(new JsonAddress(recipient))) {
            return null;
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        final List<SignalServiceAddress> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        return sendMessage(messageBuilder, membersSend);
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfo g) throws AttachmentInvalidException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(g.getMembers());

        File aFile = getGroupAvatarFile(g.groupId);
        if (aFile.exists()) {
            try {
                group.withAvatar(createAttachment(aFile));
            } catch (IOException e) {
                throw new AttachmentInvalidException(aFile.toString(), e);
            }
        }

        return SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());
    }

    public List<SendMessageResult> setExpiration(byte[] groupId, int expiresInSeconds) throws IOException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException {
        if (groupId == null) {
            return null;
        }
        GroupInfo g = getGroupForSending(groupId);

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        messageBuilder.asExpirationUpdate().withExpiration(expiresInSeconds);
        return sendMessage(messageBuilder, g.getMembers());
    }

    public List<SendMessageResult> setExpiration(SignalServiceAddress address, int expiresInSeconds) throws IOException {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();

        ThreadInfo thread = accountData.threadStore.getThread(address.getNumber().get());
        if (thread == null) {
            thread = new ThreadInfo();
            thread.id = address.getNumber().get();
        }
        thread.messageExpirationTime = expiresInSeconds;
        accountData.threadStore.updateThread(thread);

        messageBuilder.asExpirationUpdate();

        List<SignalServiceAddress> recipients = new ArrayList<>(1);
        recipients.add(address);

        return sendMessage(messageBuilder, recipients);
    }

    private List<SendMessageResult> sendGroupInfoRequest(byte[] groupId, SignalServiceAddress recipient) throws IOException {
        if (groupId == null) {
            return null;
        }

        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId);

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        final List<SignalServiceAddress> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        return sendMessage(messageBuilder, membersSend);
    }

    public void updateContact(ContactInfo contact) throws IOException {
        accountData.contactStore.updateContact(contact);
        accountData.save();
    }

    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        if (groupId.length == 0) {
            groupId = null;
        }
        if (name.isEmpty()) {
            name = null;
        }
        if (members.size() == 0) {
            members = null;
        }
        if (avatar.isEmpty()) {
            avatar = null;
        }
        return sendUpdateGroupMessage(groupId, name, members, avatar);
    }

    void requestSyncGroups() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.GROUPS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            logger.catching(e);
        }
    }

    public void requestSyncContacts() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONTACTS).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            logger.catching(e);
        }
    }

    public void requestSyncConfiguration() throws IOException {
        SignalServiceProtos.SyncMessage.Request r = SignalServiceProtos.SyncMessage.Request.newBuilder().setType(SignalServiceProtos.SyncMessage.Request.Type.CONFIGURATION).build();
        SignalServiceSyncMessage message = SignalServiceSyncMessage.forRequest(new RequestMessage(r));
        try {
            sendSyncMessage(message);
        } catch (UntrustedIdentityException e) {
            logger.catching(e);
        }
    }

    private void sendSyncMessage(SignalServiceSyncMessage message)
            throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = getMessageSender();
        try {
            messageSender.sendMessage(message, Optional.absent());
        } catch (UntrustedIdentityException e) {
            accountData.axolotlStore.identityKeyStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    public SendMessageResult sendReceipt(SignalServiceReceiptMessage message, SignalServiceAddress address) throws IOException {
        if (address == null) {
            accountData.save();
            return null;
        }

        try {
            SignalServiceMessageSender messageSender = getMessageSender();

            try {
                // TODO: this just calls sendMessage() under the hood. We should call sendMessage() directly so we can get the return value
                messageSender.sendReceipt(address, getAccessFor(address), message);
                return null;
            } catch (UntrustedIdentityException e) {
                accountData.axolotlStore.identityKeyStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                return SendMessageResult.identityFailure(address, e.getIdentityKey());
            }
        } finally {
            accountData.save();
        }
    }

    List<SendMessageResult> sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<SignalServiceAddress> recipients) throws IOException {
        if (recipients == null) {
            accountData.save();
            return Collections.emptyList();
        }

        SignalServiceDataMessage message = null;
        try {
            SignalServiceMessageSender messageSender = getMessageSender();
            message = messageBuilder.build();

            if (message.getGroupContext().isPresent()) {
                try {
                    final boolean isRecipientUpdate = false;
                    List<SendMessageResult> result = messageSender.sendMessage(new ArrayList<>(recipients), getAccessFor(recipients), isRecipientUpdate, message);
                    for (SendMessageResult r : result) {
                        if (r.getIdentityFailure() != null) {
                            accountData.axolotlStore.identityKeyStore.saveIdentity(r.getAddress().getNumber().get(), r.getIdentityFailure().getIdentityKey(), TrustLevel.UNTRUSTED);
                        }
                    }
                    return result;
                } catch (UntrustedIdentityException e) {
                    accountData.axolotlStore.identityKeyStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    return Collections.emptyList();
                }
            } else if (recipients.size() == 1 && recipients.contains(accountData.address.getSignalServiceAddress())) {
                SignalServiceAddress recipient = accountData.address.getSignalServiceAddress();
                final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessFor(recipient);
                SentTranscriptMessage transcript = new SentTranscriptMessage(Optional.of(recipient),
                        message.getTimestamp(),
                        message,
                        message.getExpiresInSeconds(),
                        Collections.singletonMap(recipient, unidentifiedAccess.isPresent()),
                        false);
                SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

                List<SendMessageResult> results = new ArrayList<>(recipients.size());
                try {
                    messageSender.sendMessage(syncMessage, unidentifiedAccess);
                } catch (UntrustedIdentityException e) {
                    accountData.axolotlStore.identityKeyStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    results.add(SendMessageResult.identityFailure(recipient, e.getIdentityKey()));
                }
                return results;
            } else {
                // Send to all individually, so sync messages are sent correctly
                List<SendMessageResult> results = new ArrayList<>(recipients.size());
                for (SignalServiceAddress address : recipients) {
                    ThreadInfo thread = accountData.threadStore.getThread(address.getLegacyIdentifier());
                    if (thread != null) {
                        messageBuilder.withExpiration(thread.messageExpirationTime);
                    } else {
                        messageBuilder.withExpiration(0);
                    }
                    message = messageBuilder.build();
                    try {
                        SendMessageResult result = messageSender.sendMessage(address, getAccessFor(address), message);
                        results.add(result);
                    } catch (UntrustedIdentityException e) {
                        accountData.axolotlStore.identityKeyStore.saveIdentity(e.getIdentifier(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                        results.add(SendMessageResult.identityFailure(address, e.getIdentityKey()));
                    }
                }
                return results;
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipients) {
                    handleEndSession(recipient);
                }
            }
            accountData.save();
        }
    }

    private static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws InvalidMetadataMessageException, InvalidMetadataVersionException, ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, ProtocolLegacyMessageException, ProtocolNoSessionException, ProtocolInvalidVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolDuplicateMessageException, SelfSendException, UnsupportedDataMessageException {
        SignalServiceCipher cipher = new SignalServiceCipher(accountData.address.getSignalServiceAddress(), accountData.axolotlStore, getCertificateValidator());
        try {
            return cipher.decrypt(envelope);
        } catch (ProtocolUntrustedIdentityException e) {
            // TODO We don't get the new untrusted identity from ProtocolUntrustedIdentityException anymore ... we need to get it from somewhere else
            // signalProtocolStore.saveIdentity(e.getSource(), e, TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private void handleEndSession(SignalServiceAddress address) {
        accountData.axolotlStore.deleteAllSessions(address);
    }

    public ThreadInfo getThread(byte[] groupId) {
        return accountData.threadStore.getThread(Base64.encodeBytes(groupId));
    }

    public ThreadInfo getThread(JsonAddress address) {
        return accountData.threadStore.getThread(address.number);
    }

    public List<SendMessageResult> send(SignalServiceDataMessage.Builder messageBuilder, JsonAddress recipientAddress, String recipientGroupId) throws GroupNotFoundException, NotAGroupMemberException, IOException, InvalidRecipientException {
        if(recipientGroupId != null && recipientAddress == null) {
            byte[] groupId = Base64.decode(recipientGroupId);
            return sendGroupMessage(messageBuilder, groupId);
        } else if(recipientAddress != null && recipientGroupId == null) {
            List<SignalServiceAddress> r = new ArrayList<>();
            r.add(recipientAddress.getSignalServiceAddress());
            return sendMessage(messageBuilder, r);
        } else {
            throw new InvalidRecipientException();
        }
    }

    public interface ReceiveMessageHandler {
        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, SignalServiceAddress source, SignalServiceAddress destination, boolean ignoreAttachments) throws GroupNotFoundException, AttachmentInvalidException, MissingConfigurationException, InvalidInputException, IOException {
        String threadId;
        if (message.getGroupContext().isPresent()) {
            SignalServiceGroup groupInfo;
            SignalServiceGroupContext groupContext = message.getGroupContext().get();

            if (!groupContext.getGroupV1().isPresent()) {
                logger.warn("Failing to handle data message with a group context but no v1 group");
                return;
            }

            // TODO: handle groups v2
//            if (groupContext.getGroupV2().isPresent()) {
//                groupInfo = groupContext.getGroupV2().get();
//            }

            groupInfo = groupContext.getGroupV1().get();
            threadId = Base64.encodeBytes(groupInfo.getGroupId());
            GroupInfo group = accountData.groupStore.getGroup(groupInfo.getGroupId());
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
                        group.addMembers(groupInfo.getMembers().get());
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
                        try {
                            sendUpdateGroupMessage(groupInfo.getGroupId(), source);
                        } catch (IOException e) {
                            logger.catching(e);
                        } catch (NotAGroupMemberException e) {
                            // We have left this group, so don't send a group update message
                        }
                    }
                    break;
            }
        } else {
            if (isSync) {
                threadId = destination.getLegacyIdentifier();
            } else {
                threadId = source.getLegacyIdentifier();
            }
        }
        if (message.isEndSession()) {
            handleEndSession(isSync ? destination : source);
        }
        if (message.isExpirationUpdate() || message.getBody().isPresent()) {
            ThreadInfo thread = accountData.threadStore.getThread(threadId);
            if (thread == null) {
                thread = new ThreadInfo();
                thread.id = threadId;
            }
            if (thread.messageExpirationTime != message.getExpiresInSeconds()) {
                thread.messageExpirationTime = message.getExpiresInSeconds();
                accountData.threadStore.updateThread(thread);
            }
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

        if(message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            if(source.equals(accountData.address.getSignalServiceAddress())) {
                if(message.getProfileKey().isPresent()) {
                    accountData.setProfileKey(message.getProfileKey().get());
                }
                accountData.save();
            } else {
                ContactInfo contact = accountData.contactStore.getContact(source);
                if(contact == null) {
                    contact = new ContactInfo();
                    contact.address = new JsonAddress(source);
                }
                contact.profileKey = Base64.encodeBytes(message.getProfileKey().get());
                updateContact(contact);
            }
        }
    }

    public void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) throws GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException, IOException, MissingConfigurationException, InvalidInputException {
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
                    if(exception == null) {
                        try {
                            handleMessage(envelope, content, ignoreAttachments);
                        } catch (GroupNotFoundException | AttachmentInvalidException | UntrustedIdentityException | InvalidInputException e) {
                            logger.catching(e);
                        }
                    }
                }
                accountData.save();
                handler.handleMessage(envelope, content, exception);
                try {
                    Files.delete(fileEntry.toPath());
                } catch (IOException e) {
                    logger.warn("Failed to delete cached message file “" + fileEntry + "”: " + e.getMessage());
                }
            }
            // Try to delete directory if empty
            dir.delete();
        }
    }

    public void shutdownMessagePipe() {
        if(messagePipe != null) {
            messagePipe.shutdown();
        }
    }

    public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler) throws IOException, MissingConfigurationException {
        try {
            retryFailedReceivedMessages(handler, ignoreAttachments);
        } catch (GroupNotFoundException | AttachmentInvalidException | UntrustedIdentityException | InvalidInputException e) {
            logger.catching(e);
        }

        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();

        try {
            if (messagePipe == null) {
                messagePipe = messageReceiver.createMessagePipe();
            }

            while (true) {
                SignalServiceEnvelope envelope;
                SignalServiceContent content = null;
                Exception exception = null;
                final long now = new Date().getTime();
                try {
                    envelope = messagePipe.read(timeout, unit, new SignalServiceMessagePipe.MessagePipeCallback() {
                        @Override
                        public void onMessage(SignalServiceEnvelope envelope) {
                            // store message on disk, before acknowledging receipt to the server
                            try {
                                File cacheFile = getMessageCacheFile(envelope, now);
                                storeEnvelope(envelope, cacheFile);
                            } catch (IOException e) {
                                logger.warn("Failed to store encrypted message in disk cache, ignoring: " + e.getMessage());
                            }
                        }
                    });
                } catch (TimeoutException e) {
                    if (returnOnTimeout)
                        return;
                    continue;
                } catch (InvalidVersionException e) {
                    logger.info("Ignoring error: " + e.getMessage());
                    continue;
                }
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        exception = e;
                    }
                    if(exception == null) {
                        try {
                            handleMessage(envelope, content, ignoreAttachments);
                        } catch (GroupNotFoundException | AttachmentInvalidException | UntrustedIdentityException | InvalidInputException e) {
                            logger.catching(e);
                        }
                    }
                }
                accountData.save();
                handler.handleMessage(envelope, content, exception);
                File cacheFile = null;
                try {
                    cacheFile = getMessageCacheFile(envelope, now);
                    Files.delete(cacheFile.toPath());
                    // Try to delete directory if empty
                    new File(getMessageCachePath()).delete();
                } catch (IOException e) {
                    logger.warn("Failed to delete cached message file “" + cacheFile + "”: " + e.getMessage());
                }
            }
        } finally {
            if (messagePipe != null) {
                messagePipe.shutdown();
                messagePipe = null;
            }
        }
    }

    private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments) throws GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException, IOException, MissingConfigurationException, InvalidInputException {
        if (content != null) {
            SignalServiceAddress source = envelope.hasSource() ? envelope.getSourceAddress() : content.getSender();
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();
                handleSignalServiceDataMessage(message, false, source, accountData.address.getSignalServiceAddress(), ignoreAttachments);
            }
            if (content.getSyncMessage().isPresent()) {
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();
                    handleSignalServiceDataMessage(message, true, source, syncMessage.getSent().get().getDestination().orNull(), ignoreAttachments);
                }
                if (syncMessage.getRequest().isPresent()) {
                    RequestMessage rm = syncMessage.getRequest().get();
                    if (rm.isContactsRequest()) {
                        try {
                            sendContacts();
                        } catch (UntrustedIdentityException | IOException e) {
                            logger.catching(e);
                        }
                    }
                    if (rm.isGroupsRequest()) {
                        try {
                            sendGroups();
                        } catch (UntrustedIdentityException | IOException e) {
                            logger.catching(e);
                        }
                    }
                }

                if (syncMessage.getGroups().isPresent()) {
                    File tmpFile = null;
                    try {
                        tmpFile = Util.createTempFile();
                        try (InputStream attachmentAsStream = retrieveAttachmentAsStream(syncMessage.getGroups().get().asPointer(), tmpFile)) {
                            DeviceGroupsInputStream s = new DeviceGroupsInputStream(attachmentAsStream);
                            DeviceGroup g;
                            logger.debug("Sync message included new groups!");
                            while ((g = s.read()) != null) {
                                accountData.groupStore.updateGroup(new GroupInfo(g));
                                if (g.getAvatar().isPresent()) {
                                    retrieveGroupAvatarAttachment(g.getAvatar().get(), g.getId());
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.catching(e);
                    } finally {
                        if (tmpFile != null) {
                            try {
                                Files.delete(tmpFile.toPath());
                            } catch (IOException e) {
                                logger.warn("Failed to delete received groups temp file “" + tmpFile + "”: " + e.getMessage());
                            }
                        }
                    }
                    if (syncMessage.getBlockedList().isPresent()) {
                        // TODO store list of blocked numbers
                    }
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
                                ContactInfo contact = accountData.contactStore.getContact(c.getAddress());
                                if (contact == null) {
                                    contact = new ContactInfo();
                                    contact.address = new JsonAddress(c.getAddress());
                                }
                                if (c.getName().isPresent()) {
                                    contact.name = c.getName().get();
                                }
                                if (c.getColor().isPresent()) {
                                    contact.color = c.getColor().get();
                                }

                                if(c.getProfileKey().isPresent()) {
                                    contact.profileKey = Base64.encodeBytes(c.getProfileKey().get().serialize());
                                }
                                updateContact(contact);

                                if (c.getAvatar().isPresent()) {
                                    retrieveContactAvatarAttachment(c.getAvatar().get(), contact.address.getSignalServiceAddress());
                                }
                            }
                        }
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
                    accountData.axolotlStore.identityKeyStore.saveIdentity(verifiedMessage.getDestination().getLegacyIdentifier(), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
            }
        }
    }

    private SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        logger.debug("Loading cached envelope from " + file.toString());
        try (FileInputStream f = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(f);
            int version = in.readInt();
            if (version > 3) {
                return null;
            }
            int type = in.readInt();
            String source = in.readUTF();
            UUID sourceUuid = null;
            if (version >= 3) {
                sourceUuid = UuidUtil.parseOrNull(in.readUTF());
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
            long serverTimestamp = 0;
            String uuid = null;
            if (version == 2) {
                serverTimestamp = in.readLong();
                uuid = in.readUTF();
                if ("".equals(uuid)) {
                    uuid = null;
                }
            }
            Optional<SignalServiceAddress> sourceAddress = sourceUuid == null && source.isEmpty()
                    ? Optional.absent()
                    : Optional.of(new SignalServiceAddress(sourceUuid, source));
            return new SignalServiceEnvelope(type, sourceAddress, sourceDevice, timestamp, legacyMessage, content, serverTimestamp, uuid);
}
    }

    private void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        logger.debug("Storing envelope to " + file.toString());
        try (FileOutputStream f = new FileOutputStream(file)) {
            try (DataOutputStream out = new DataOutputStream(f)) {
                out.writeInt(2); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSourceE164().isPresent() ? envelope.getSourceE164().get() : "");
                out.writeUTF(envelope.getSourceUuid().isPresent() ? envelope.getSourceUuid().get() : "");
                out.writeInt(envelope.getSourceDevice());
                out.writeLong(envelope.getTimestamp());
                if (envelope.hasContent()) {
                    out.writeInt(envelope.getContent().length);
                    out.write(envelope.getContent());
                } else {
                    out.writeInt(0);
                }
                if (envelope.hasLegacyMessage()) {
                    out.writeInt(envelope.getLegacyMessage().length);
                    out.write(envelope.getLegacyMessage());
                } else {
                    out.writeInt(0);
                }
                out.writeLong(envelope.getServerTimestamp());
                String uuid = envelope.getUuid();
                out.writeUTF(uuid == null ? "" : uuid);
            }
}
    }

    public File getContactAvatarFile(SignalServiceAddress address) {
        return new File(avatarsPath, "contact-" + address.getNumber());
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, SignalServiceAddress address) throws IOException, InvalidMessageException, MissingConfigurationException {
        createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getContactAvatarFile(address), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return retrieveAttachment(stream, getContactAvatarFile(address));
        }
    }

    public File getGroupAvatarFile(byte[] groupId) {
        return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_"));
    }

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

    public File getAttachmentFile(long attachmentId) {
        return new File(attachmentsPath, attachmentId + "");
    }

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException, MissingConfigurationException {
        createPrivateDirectories(attachmentsPath);
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getRemoteId().getV2().get()), true);
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

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException, MissingConfigurationException {
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

        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();

        File tmpFile = Util.createTempFile();
        try (InputStream input = messageReceiver.retrieveAttachment(pointer, tmpFile, MAX_ATTACHMENT_SIZE)) {
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
        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();
        return messageReceiver.retrieveAttachment(pointer, tmpFile, MAX_ATTACHMENT_SIZE);
    }

    private String canonicalizeNumber(String number) throws InvalidNumberException {
        String localNumber = accountData.username;
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    private void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = Util.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : accountData.groupStore.getGroups()) {
                    Optional<Integer> expirationTimer = Optional.absent();
                    Optional<String> color = Optional.absent();
                    out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name),
                            record.getMembers(), createGroupAvatarAttachment(record.groupId),
                            record.active, expirationTimer, color, false, Optional.absent(), false));
                }
            }

            if (groupsFile.exists() && groupsFile.length() > 0) {
                try (FileInputStream groupsFileStream = new FileInputStream(groupsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(groupsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(groupsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forGroups(attachmentStream));
                }
            }
        } finally {
            try {
                Files.delete(groupsFile.toPath());
            } catch (IOException e) {
                logger.warn("Failed to delete groups temp file " + groupsFile + ": " + e.getMessage());
            }
        }
    }

    private void sendContacts() throws IOException, UntrustedIdentityException {
        File contactsFile = Util.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(contactsFile)) {
                DeviceContactsOutputStream out = new DeviceContactsOutputStream(fos);
                for (ContactInfo record : accountData.contactStore.getContacts()) {
                    VerifiedMessage verifiedMessage = null;
                    if (getIdentities().containsKey(record.address.number)) {
                        IdentityKeyStore.Identity currentIdentity = null;
                        for (IdentityKeyStore.Identity id : getIdentities().get(record.address.number)) {
                            if (currentIdentity == null || id.getDateAdded().after(currentIdentity.getDateAdded())) {
                                currentIdentity = id;
                            }
                        }
                        if (currentIdentity != null) {
                            verifiedMessage = new VerifiedMessage(record.address.getSignalServiceAddress(), currentIdentity.getIdentityKey(), currentIdentity.getTrustLevel().toVerifiedState(), currentIdentity.getDateAdded().getTime());
                        }
                    }

                    // TODO include profile key
                    // TODO: Don't hard code `false` value for blocked argument
                    Optional<Integer> expirationTimer = Optional.absent();
                    out.write(new DeviceContact(record.address.getSignalServiceAddress(), Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.address.getSignalServiceAddress()), Optional.fromNullable(record.color),
                            Optional.fromNullable(verifiedMessage), Optional.absent(), false, expirationTimer, Optional.absent(), false));
                }
            }

            if (contactsFile.exists() && contactsFile.length() > 0) {
                try (FileInputStream contactsFileStream = new FileInputStream(contactsFile)) {
                    SignalServiceAttachmentStream attachmentStream = SignalServiceAttachment.newStreamBuilder()
                            .withStream(contactsFileStream)
                            .withContentType("application/octet-stream")
                            .withLength(contactsFile.length())
                            .build();

                    sendSyncMessage(SignalServiceSyncMessage.forContacts(new ContactsMessage(attachmentStream, true)));
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

    private void sendVerifiedMessage(SignalServiceAddress destination, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination, identityKey, trustLevel.toVerifiedState(), System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public List<ContactInfo> getContacts() {
      if(accountData.contactStore == null) {
        return Collections.emptyList();
      }
      return this.accountData.contactStore.getContacts();
    }

    public ContactInfo getContact(SignalServiceAddress address) {
        return accountData.contactStore.getContact(address.getNumber().get());
    }

    public GroupInfo getGroup(byte[] groupId) {
        return accountData.groupStore.getGroup(groupId);
    }

    public Map<String, List<IdentityKeyStore.Identity>> getIdentities() {
        return accountData.axolotlStore.identityKeyStore.getIdentities();
    }

    public List<IdentityKeyStore.Identity> getIdentities(SignalServiceAddress address) {
        return accountData.axolotlStore.identityKeyStore.getIdentities(address);
    }

    public boolean trustIdentity(SignalServiceAddress address, byte[] fingerprint, TrustLevel level) throws IOException {
        List<IdentityKeyStore.Identity> ids = accountData.axolotlStore.identityKeyStore.getIdentities(address.getLegacyIdentifier());
        if (ids == null) {
            return false;
        }
        for (IdentityKeyStore.Identity id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            accountData.axolotlStore.identityKeyStore.saveIdentity(address.getLegacyIdentifier(), id.getIdentityKey(), level);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), level);
            } catch (IOException | UntrustedIdentityException e) {
                logger.catching(e);
            }
            accountData.save();
            return true;
        }
        return false;
    }

    public boolean trustIdentitySafetyNumber(SignalServiceAddress address, String safetyNumber, TrustLevel level) throws IOException {
        List<IdentityKeyStore.Identity> ids = accountData.axolotlStore.identityKeyStore.getIdentities(address.getLegacyIdentifier());
        if (ids == null) {
            return false;
        }
        for (IdentityKeyStore.Identity id : ids) {
            if (!safetyNumber.equals(SafetyNumberHelper.computeSafetyNumber(accountData.username, getIdentity(), address.getLegacyIdentifier(), id.getIdentityKey()))) {
                continue;
            }

            accountData.axolotlStore.identityKeyStore.saveIdentity(address.getLegacyIdentifier(), id.getIdentityKey(), level);
            try {
                sendVerifiedMessage(address, id.getIdentityKey(), level);
            } catch (IOException | UntrustedIdentityException e) {
                logger.catching(e);
            }
            accountData.save();
            return true;
        }
        return false;
    }

    public Optional<ContactTokenDetails> getUser(String e164number) throws IOException {
        return accountManager.getContact(e164number);
    }

    public List<Optional<UnidentifiedAccessPair>> getAccessFor(Collection<SignalServiceAddress> recipients) {
        List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
        for (SignalServiceAddress ignored : recipients) {
            result.add(Optional.absent());
        }
        return result;
    }

    public Optional<UnidentifiedAccessPair> getAccessFor(SignalServiceAddress recipient) {
        // TODO implement
        return Optional.absent();
    }

    public void setProfileName(String name) throws IOException, InvalidInputException {
        accountManager.setProfileName(accountData.getProfileKey(), name);
	    accountData.save();
    }

    public SignalServiceProfile getProfile(SignalServiceAddress address) throws IOException, VerificationFailedException {
        final SignalServiceMessageReceiver messageReceiver = getMessageReceiver();
        return messageReceiver.retrieveProfile(address, null, Optional.absent(), null).getProfile();
    }

    private SignalServiceMessageSender getMessageSender() {
        return new SignalServiceMessageSender(serviceConfiguration,
                accountData.address.getUUID(), accountData.username, accountData.password, accountData.deviceId,
                accountData.axolotlStore, BuildConfig.SIGNAL_AGENT, true, false, Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.absent(), null);
    }

    private SignalServiceMessageReceiver getMessageReceiver() {
        return new SignalServiceMessageReceiver(serviceConfiguration, accountData.address.getUUID(), accountData.username, accountData.password, accountData.deviceId, accountData.signalingKey, USER_AGENT, null, sleepTimer, null);
    }
}
