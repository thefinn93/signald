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

import io.finn.signald.exceptions.InvalidStorageFileException;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.IdentityKeyStore;
import io.finn.signald.storage.SignalProtocolStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.*;
import org.asamk.signal.storage.contacts.ContactInfo;
import org.asamk.signal.storage.groups.GroupInfo;
import org.asamk.signal.storage.threads.ThreadInfo;
import org.signal.libsignal.metadata.*;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECKeyPair;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
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
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.messages.multidevice.*;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.push.exceptions.*;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalContactDiscoveryUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalServiceUrl;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.UnsupportedDataMessageException;
import org.whispersystems.signalservice.internal.util.Base64;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
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
    private Logger logger;
    private final static TrustStore TRUST_STORE = new WhisperTrustStore();
    private final static SignalServiceConfiguration serviceConfiguration = new SignalServiceConfiguration(
            new SignalServiceUrl[]{new SignalServiceUrl(BuildConfig.SIGNAL_URL, TRUST_STORE)},
            new SignalCdnUrl[]{new SignalCdnUrl(BuildConfig.SIGNAL_CDN_URL, TRUST_STORE)},
	    new SignalContactDiscoveryUrl[]{new SignalContactDiscoveryUrl(BuildConfig.SIGNAL_CONTACT_DISCOVERY_URL, TRUST_STORE)}
    );

    public final static String PROJECT_NAME = BuildConfig.NAME;
    public final static String PROJECT_VERSION = BuildConfig.VERSION;
    private final static String USER_AGENT = BuildConfig.USER_AGENT;

    private final static int PREKEY_MINIMUM_COUNT = 20;
    private final static int PREKEY_BATCH_SIZE = 100;
    private final static int MAX_ATTACHMENT_SIZE = 150 * 1024 * 1024;

    private static ConcurrentHashMap<String,Manager> managers = new ConcurrentHashMap<>();

    private static String settingsPath;
    private static String dataPath;
    private static String attachmentsPath;
    private static String avatarsPath;

    private FileChannel fileChannel;
    private FileLock lock;
    private AccountData accountData;

//    private final ObjectMapper jsonProcessor = new ObjectMapper();

    private SignalServiceAccountManager accountManager;
    private SignalServiceMessagePipe messagePipe = null;
    private SignalServiceMessagePipe unidentifiedMessagePipe = null;

    private UptimeSleepTimer sleepTimer = new UptimeSleepTimer();

    public static Manager get(String username) throws IOException, NoSuchAccountException, InvalidStorageFileException {
        return get(username, false);
    }

    public static Manager get(String username, boolean newUser) throws IOException, NoSuchAccountException, InvalidStorageFileException {
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

    public static void setDataPath(String path) {
        settingsPath = path;
        dataPath = settingsPath + "/data";
        attachmentsPath = settingsPath + "/attachments";
        avatarsPath = settingsPath + "/avatars";
    }

    public String getUsername() {
        return accountData.username;
    }

    private IdentityKey getIdentity() {
        return accountData.axolotlStore.identityKeyStore.getIdentityKeyPair().getPublicKey();
    }

    public int getDeviceId() {
        return accountData.deviceId;
    }

    public String getFileName() {
        return dataPath + "/" + accountData.username;
    }

    private String getMessageCachePath() {
        return this.dataPath + "/" + accountData.username + ".d/msg-cache";
    }

    private String getMessageCachePath(String sender) {
        return getMessageCachePath() + "/" + sender.replace("/", "_");
    }

    private File getMessageCacheFile(String sender, long now, long timestamp) throws IOException {
        String cachePath = getMessageCachePath(sender);
        createPrivateDirectories(cachePath);
        return new File(cachePath + "/" + now + "_" + timestamp);
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

    private static void createPrivateFile(String path) throws IOException {
        final Path file = new File(path).toPath();
        try {
            Set<PosixFilePermission> perms = EnumSet.of(OWNER_READ, OWNER_WRITE);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(perms));
        } catch (UnsupportedOperationException e) {
            Files.createFile(file);
        }
    }

    public boolean userExists() {
        if (accountData.username == null) {
            return false;
        }
        File f = new File(getFileName());
        return !(!f.exists() || f.isDirectory());
    }

    public boolean userHasKeys() {
        return accountData.axolotlStore != null;
    }

    public void init() throws IOException {
        accountData = AccountData.load(new File(getFileName()));
        accountManager = new SignalServiceAccountManager(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, USER_AGENT, sleepTimer);
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
        accountData.init();
    }

    public boolean isRegistered() {
        return accountData.registered;
    }

    public void register(boolean voiceVerification, Optional<String> captcha) throws IOException {
        accountData.password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(serviceConfiguration, accountData.username, accountData.password, USER_AGENT, sleepTimer);

        if (voiceVerification) {
            accountManager.requestVoiceVerificationCode(Locale.getDefault(), captcha, Optional.absent());  // TODO: Allow requester to set the locale
        } else {
            accountManager.requestSmsVerificationCode(true, captcha, Optional.absent()); //  TODO: Allow requester to set challenge
        }

        accountData.registered = false;
        accountData.init();
        accountData.save();
    }

    public void unregister() throws IOException {
        // When setting an empty GCM id, the Signal-Server also sets the fetchesMessages property to false.
        // If this is the master device, other users can't send messages to this number anymore.
        // If this is a linked device, other users can still send messages, but this device doesn't receive them anymore.
        accountManager.setGcmId(Optional.<String>absent());
    }

    public URI getDeviceLinkUri() throws TimeoutException, IOException {
        accountData.password = Util.getSecret(18);

        accountManager = new SignalServiceAccountManager(serviceConfiguration, accountData.username, accountData.password, USER_AGENT, sleepTimer);
        String uuid = accountManager.getNewDeviceUuid();

        accountData.registered = false;
        try {
            return new URI("tsdevice:/?uuid=" + URLEncoder.encode(uuid, "utf-8") + "&pub_key=" + URLEncoder.encode(Base64.encodeBytesWithoutPadding(accountData.axolotlStore.identityKeyStore.getIdentityKeyPair().getPublicKey().serialize()), "utf-8"));
        } catch (URISyntaxException e) {
            // Shouldn't happen
            return null;
        }
    }

    public void finishDeviceLink(String deviceName) throws IOException, InvalidKeyException, TimeoutException, UserAlreadyExists {
        accountData.signalingKey = Util.getSecret(52);
        SignalServiceAccountManager.NewDeviceRegistrationReturn ret = accountManager.finishNewDeviceRegistration(accountData.axolotlStore.identityKeyStore.getIdentityKeyPair(), accountData.signalingKey, false, true, accountData.axolotlStore.identityKeyStore.getLocalRegistrationId(), deviceName);
        accountData.deviceId = ret.getDeviceId();
        accountData.username = ret.getNumber();
        // TODO do this check before actually registering
        if (userExists()) {
            throw new UserAlreadyExists(accountData.username, getFileName());
        }
        accountData.axolotlStore = new SignalProtocolStore(ret.getIdentity(), accountData.axolotlStore.identityKeyStore.getLocalRegistrationId());

        accountData.registered = true;
        refreshPreKeys();

        accountData.init();

        requestSyncGroups();
        requestSyncContacts();

        accountData.save();
        managers.put(accountData.username, this);
        logger.info("Successfully finished linked to " + Util.redact(accountData.username) + " as device #" + accountData.deviceId);
    }

    public List<DeviceInfo> getLinkedDevices() throws IOException {
        return accountManager.getDevices();
    }

    public void removeLinkedDevices(int deviceId) throws IOException {
        accountManager.removeDevice(deviceId);
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
        accountManager.addDevice(deviceIdentifier, deviceKey, identityKeyPair, Optional.<byte[]>absent(), verificationCode);
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

    public void verifyAccount(String verificationCode) throws IOException {
        verificationCode = verificationCode.replace("-", "");
        accountData.signalingKey = Util.getSecret(52);
        accountManager.verifyAccountWithCode(verificationCode, accountData.signalingKey, accountData.axolotlStore.getLocalRegistrationId(), true, null, null, false);

        //accountManager.setGcmId(Optional.of(GoogleCloudMessaging.getInstance(this).register(REGISTRATION_ID)));
        accountData.registered = true;

        refreshPreKeys();
        accountData.init();
        accountData.save();
    }

    private void refreshPreKeys() throws IOException {
        List<PreKeyRecord> oneTimePreKeys = generatePreKeys();
        SignedPreKeyRecord signedPreKeyRecord = generateSignedPreKey(accountData.axolotlStore.getIdentityKeyPair());

        accountManager.setPreKeys(accountData.axolotlStore.getIdentityKeyPair().getPublicKey(), signedPreKeyRecord, oneTimePreKeys);
    }


    private static List<SignalServiceAttachment> getSignalServiceAttachments(List<String> attachments) throws AttachmentInvalidException {
        List<SignalServiceAttachment> SignalServiceAttachments = null;
        if (attachments != null) {
            SignalServiceAttachments = new ArrayList<>(attachments.size());
            for (String attachment : attachments) {
                try {
                    SignalServiceAttachments.add(createAttachment(new File(attachment)));
                } catch (IOException e) {
                    throw new AttachmentInvalidException(attachment, e);
                }
            }
        }
        return SignalServiceAttachments;
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
        return new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), false, Optional.<byte[]>absent(), 0, 0, caption, Optional.<String>absent(), null);
    }

    private Optional<SignalServiceAttachmentStream> createGroupAvatarAttachment(byte[] groupId) throws IOException {
        File file = getGroupAvatarFile(groupId);
        if (!file.exists()) {
            return Optional.absent();
        }

        return Optional.of(createAttachment(file));
    }

    private Optional<SignalServiceAttachmentStream> createContactAvatarAttachment(String number) throws IOException {
        File file = getContactAvatarFile(number);
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
        for (String member : g.members) {
            if (member.equals(accountData.username)) {
                return g;
            }
        }
        throw new NotAGroupMemberException(groupId, g.name);
    }

    public List<GroupInfo> getGroups() {
        return accountData.groupStore.getGroups();
    }

    public List<SendMessageResult> sendGroupMessage(String messageText, List<SignalServiceAttachment> attachments, byte[] groupId, SignalServiceDataMessage.Quote quote)
            throws IOException, EncapsulatedExceptions, UntrustedIdentityException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(attachments);
        }
        if (groupId != null) {
            SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.DELIVER)
                    .withId(groupId)
                    .build();
            messageBuilder.asGroupMessage(group);
        }
        if(quote != null) {
          messageBuilder.withQuote(quote);
        }
        ThreadInfo thread = accountData.threadStore.getThread(Base64.encodeBytes(groupId));
        if (thread != null) {
            messageBuilder.withExpiration(thread.messageExpirationTime);
        }

        final GroupInfo g = getGroupForSending(groupId);

        // Don't send group message to ourself
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(accountData.username);
        return sendMessage(messageBuilder, membersSend);
    }

    public List<SendMessageResult> sendQuitGroupMessage(byte[] groupId) throws GroupNotFoundException, IOException, EncapsulatedExceptions, UntrustedIdentityException, NotAGroupMemberException {
        SignalServiceGroup group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.QUIT)
                .withId(groupId)
                .build();

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group);

        final GroupInfo g = getGroupForSending(groupId);
        g.members.remove(accountData.username);
        accountData.groupStore.updateGroup(g);

        return sendMessage(messageBuilder, g.members);
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

    public byte[] sendUpdateGroupMessage(byte[] groupId, String name, Collection<String> members, String avatarFile) throws IOException, EncapsulatedExceptions, UntrustedIdentityException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
        GroupInfo g;
        if (groupId == null) {
            // Create new group
            g = new GroupInfo(Util.getSecretBytes(16));
            g.members.add(accountData.username);
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
                if (g.members.contains(member)) {
                    continue;
                }
                newMembers.add(member);
                g.members.add(member);
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
        final List<String> membersSend = new ArrayList<>(g.members);
        membersSend.remove(accountData.username);
        sendMessage(messageBuilder, membersSend);
        return g.groupId;
    }

    private List<SendMessageResult> sendUpdateGroupMessage(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions, UntrustedIdentityException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException {
        if (groupId == null) {
            return null;
        }
        GroupInfo g = getGroupForSending(groupId);

        if (!g.members.contains(recipient)) {
            return null;
        }

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        // Send group message only to the recipient who requested it
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        return sendMessage(messageBuilder, membersSend);
    }

    private SignalServiceDataMessage.Builder getGroupUpdateMessageBuilder(GroupInfo g) throws AttachmentInvalidException {
        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.UPDATE)
                .withId(g.groupId)
                .withName(g.name)
                .withMembers(new ArrayList<>(g.members));

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

    public List<SendMessageResult> setExpiration(byte[] groupId, int expiresInSeconds) throws IOException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException, EncapsulatedExceptions, UntrustedIdentityException {
        if (groupId == null) {
            return null;
        }
        GroupInfo g = getGroupForSending(groupId);

        SignalServiceDataMessage.Builder messageBuilder = getGroupUpdateMessageBuilder(g);

        messageBuilder.asExpirationUpdate().withExpiration(expiresInSeconds);
        return sendMessage(messageBuilder, new ArrayList<>(g.members));
    }

    public List<SendMessageResult> setExpiration(String recipient, int expiresInSeconds) throws IOException, UntrustedIdentityException, EncapsulatedExceptions {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();

        ThreadInfo thread = accountData.threadStore.getThread(recipient);
        if (thread == null) {
            thread = new ThreadInfo();
            thread.id = recipient;
        }
        thread.messageExpirationTime = expiresInSeconds;
        accountData.threadStore.updateThread(thread);

        messageBuilder.asExpirationUpdate();

        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);

        return sendMessage(messageBuilder, recipients);
    }

    private List<SendMessageResult> sendGroupInfoRequest(byte[] groupId, String recipient) throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        if (groupId == null) {
            return null;
        }

        SignalServiceGroup.Builder group = SignalServiceGroup.newBuilder(SignalServiceGroup.Type.REQUEST_INFO)
                .withId(groupId);

        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asGroupMessage(group.build());

        // Send group info request message to the recipient who sent us a message with this groupId
        final List<String> membersSend = new ArrayList<>();
        membersSend.add(recipient);
        return sendMessage(messageBuilder, membersSend);
    }

    public List<SendMessageResult> sendMessage(String message, List<SignalServiceAttachment> attachments, String recipient, SignalServiceDataMessage.Quote quote)
            throws EncapsulatedExceptions, UntrustedIdentityException, AttachmentInvalidException, IOException {
        List<String> recipients = new ArrayList<>(1);
        recipients.add(recipient);
        return sendMessage(message, attachments, recipients, quote);
    }

    public List<SendMessageResult> sendMessage(String messageText, List<SignalServiceAttachment> attachments, List<String> recipients, SignalServiceDataMessage.Quote quote)
            throws IOException, EncapsulatedExceptions, UntrustedIdentityException, AttachmentInvalidException {
        final SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().withBody(messageText);
        if (attachments != null) {
            messageBuilder.withAttachments(attachments);
        }
        if(quote != null) {
          messageBuilder.withQuote(quote);
        }
        return sendMessage(messageBuilder, recipients);
    }

    public List<SendMessageResult> sendEndSessionMessage(List<String> recipients) throws IOException, EncapsulatedExceptions, UntrustedIdentityException {
        SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder().asEndSessionMessage();

        return sendMessage(messageBuilder, recipients);
    }

    public String getContactName(String number) {
        ContactInfo contact = accountData.contactStore.getContact(number);
        if (contact == null) {
            return "";
        } else {
            return contact.name;
        }
    }

    public void setContactName(String number, String name) throws IOException {
        ContactInfo contact = accountData.contactStore.getContact(number);
        if (contact == null) {
            contact = new ContactInfo();
            contact.number = number;
            logger.debug("Add contact " + Util.redact(number));
        } else {
            logger.debug("Updating contact " + Util.redact(number));
        }
        contact.name = name;
        accountData.contactStore.updateContact(contact);
        accountData.save();
    }

    public void updateContact(ContactInfo contact) throws IOException {
        accountData.contactStore.updateContact(contact);
        accountData.save();
    }

    public String getGroupName(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return "";
        } else {
            return group.name;
        }
    }

    public List<String> getGroupMembers(byte[] groupId) {
        GroupInfo group = getGroup(groupId);
        if (group == null) {
            return new ArrayList<String>();
        } else {
            return new ArrayList<String>(group.members);
        }
    }

    public byte[] updateGroup(byte[] groupId, String name, List<String> members, String avatar) throws IOException, EncapsulatedExceptions, UntrustedIdentityException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
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

    private void requestSyncGroups() throws IOException {
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

    private void sendSyncMessage(SignalServiceSyncMessage message)
            throws IOException, UntrustedIdentityException {
        SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceConfiguration, accountData.username, accountData.password,
                accountData.deviceId, accountData.axolotlStore, USER_AGENT, true, Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());
        try {
            messageSender.sendMessage(message, Optional.<UnidentifiedAccessPair>absent());
        } catch (UntrustedIdentityException e) {
            accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
            throw e;
        }
    }


    private void legacySendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients)
            throws UntrustedIdentityException, EncapsulatedExceptions, IOException {
        legacySendMessage(messageBuilder, recipients, true);
    }

    private void legacySendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients, boolean useExistingExpiration)
            throws EncapsulatedExceptions, UntrustedIdentityException, UntrustedIdentityException, IOException {
        Set<SignalServiceAddress> recipientsTS = getSignalServiceAddresses(recipients);
        if (recipientsTS == null) return;

        if(accountData.profileKey != null) {
            messageBuilder = messageBuilder.withProfileKey(accountData.getProfileKey());
        }

        SignalServiceDataMessage message = null;
        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.axolotlStore, USER_AGENT, true, Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());

            // Send to all individually, so sync messages are sent correctly
            List<UntrustedIdentityException> untrustedIdentities = new LinkedList<>();
            List<UnregisteredUserException> unregisteredUsers = new LinkedList<>();
            List<NetworkFailureException> networkExceptions = new LinkedList<>();
            for(SignalServiceAddress address: recipientsTS) {
                ThreadInfo thread = accountData.threadStore.getThread(address.getNumber());
                if(useExistingExpiration) {
                    if (thread != null) {
                        messageBuilder.withExpiration(thread.messageExpirationTime);
                    } else {
                        messageBuilder.withExpiration(0);
                    }
                }
                message = messageBuilder.build();
                try {
                    messageSender.sendMessage(address, getAccessFor(address), message);
                } catch(UntrustedIdentityException e) {
                    accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    untrustedIdentities.add(e);
                    logger.warn("UntrustedIdentityException sending message to " + Util.redact(e.getE164Number()));
                } catch(UnregisteredUserException e) {
                    unregisteredUsers.add(e);
                    logger.warn("UnregisteredUserException when sending message to %s" + Util.redact(address.getNumber()));
                } catch(PushNetworkException e) {
                    networkExceptions.add(new NetworkFailureException(address.getNumber(), e));
                    logger.warn("PushNetworkException when sending message to %s" + Util.redact(address.getNumber()));
                }

                if (!untrustedIdentities.isEmpty() || !unregisteredUsers.isEmpty() || !networkExceptions.isEmpty()) {
                    throw new EncapsulatedExceptions(untrustedIdentities, unregisteredUsers, networkExceptions);
                }
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipientsTS) {
                    handleEndSession(recipient.getNumber());
                }
            }
            accountData.save();
        }
    }

    public SendMessageResult sendReceipt(SignalServiceReceiptMessage message, String recipient) throws IOException {
        SignalServiceAddress address = getSignalServiceAddress(recipient);
        if (address == null) {
            accountData.save();
            return null;
        }

        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.axolotlStore, USER_AGENT, true, Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());

            try {
                messageSender.sendReceipt(address, getAccessFor(address), message);
		return null;
            } catch (UntrustedIdentityException e) {
                accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                return SendMessageResult.identityFailure(address, e.getIdentityKey());
            }
        } finally {
            accountData.save();
        }
    }

    private List<SendMessageResult> sendMessage(SignalServiceDataMessage.Builder messageBuilder, Collection<String> recipients) throws IOException {
        Set<SignalServiceAddress> recipientsTS = getSignalServiceAddresses(recipients);
        if (recipientsTS == null) {
            accountData.save();
            return Collections.emptyList();
        }

        SignalServiceDataMessage message = null;
        try {
            SignalServiceMessageSender messageSender = new SignalServiceMessageSender(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.axolotlStore, USER_AGENT, true, Optional.fromNullable(messagePipe), Optional.fromNullable(unidentifiedMessagePipe), Optional.<SignalServiceMessageSender.EventListener>absent());

            message = messageBuilder.build();
            if (message.getGroupInfo().isPresent()) {
                try {
                    final boolean isRecipientUpdate = false;
                    List<SendMessageResult> result = messageSender.sendMessage(new ArrayList<>(recipientsTS), getAccessFor(recipientsTS), isRecipientUpdate, message);
                    for (SendMessageResult r : result) {
                        if (r.getIdentityFailure() != null) {
                            accountData.axolotlStore.identityKeyStore.saveIdentity(r.getAddress().getNumber(), r.getIdentityFailure().getIdentityKey(), TrustLevel.UNTRUSTED);
                        }
                    }
                    return result;
                } catch (UntrustedIdentityException e) {
                    accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    return Collections.emptyList();
                }
            } else if (recipientsTS.size() == 1 && recipientsTS.contains(new SignalServiceAddress(accountData.password))) {
                SignalServiceAddress recipient = new SignalServiceAddress(accountData.username);
                final Optional<UnidentifiedAccessPair> unidentifiedAccess = getAccessFor(recipient);
                SentTranscriptMessage transcript = new SentTranscriptMessage(recipient.getNumber(),
                        message.getTimestamp(),
                        message,
                        message.getExpiresInSeconds(),
                        Collections.singletonMap(recipient.getNumber(), unidentifiedAccess.isPresent()),
                        false);
                SignalServiceSyncMessage syncMessage = SignalServiceSyncMessage.forSentTranscript(transcript);

                List<SendMessageResult> results = new ArrayList<>(recipientsTS.size());
                try {
                    messageSender.sendMessage(syncMessage, unidentifiedAccess);
                } catch (UntrustedIdentityException e) {
                    accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                    results.add(SendMessageResult.identityFailure(recipient, e.getIdentityKey()));
                }
                return results;
            } else {
                // Send to all individually, so sync messages are sent correctly
                List<SendMessageResult> results = new ArrayList<>(recipientsTS.size());
                for (SignalServiceAddress address : recipientsTS) {
                    ThreadInfo thread = accountData.threadStore.getThread(address.getNumber());
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
                        accountData.axolotlStore.identityKeyStore.saveIdentity(e.getE164Number(), e.getIdentityKey(), TrustLevel.UNTRUSTED);
                        results.add(SendMessageResult.identityFailure(address, e.getIdentityKey()));
                    }
                }
                return results;
            }
        } finally {
            if (message != null && message.isEndSession()) {
                for (SignalServiceAddress recipient : recipientsTS) {
                    handleEndSession(recipient.getNumber());
                }
            }
            accountData.save();
        }
    }

    private SignalServiceAddress getSignalServiceAddress(String recipient) throws IOException {
        try {
            return getPushAddress(recipient);
        } catch (InvalidNumberException e) {
            logger.warn("Failed to add recipient \"" + Util.redact(recipient) + "\": " + e.getMessage());
            logger.warn("Aborting sending.");
            accountData.save();
            return null;
        }
    }

    private Set<SignalServiceAddress> getSignalServiceAddresses(Collection<String> recipients) throws IOException {
        Set<SignalServiceAddress> recipientsTS = new HashSet<>(recipients.size());
        for (String recipient : recipients) {
            SignalServiceAddress addr = getSignalServiceAddress(recipient);
            if (addr == null)
                return null;
            recipientsTS.add(addr);
        }
        return recipientsTS;
    }

    private static CertificateValidator getCertificateValidator() {
        try {
            ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
            return new CertificateValidator(unidentifiedSenderTrustRoot);
        } catch (InvalidKeyException | IOException e) {
            throw new AssertionError(e);
        }
    }

    private SignalServiceContent decryptMessage(SignalServiceEnvelope envelope) throws NoSessionException, LegacyMessageException, InvalidVersionException, InvalidMessageException, DuplicateMessageException, InvalidKeyException, InvalidKeyIdException, org.whispersystems.libsignal.UntrustedIdentityException, InvalidMetadataMessageException, InvalidMetadataVersionException, UntrustedIdentityException, ProtocolInvalidKeyIdException, ProtocolUntrustedIdentityException, ProtocolLegacyMessageException, ProtocolNoSessionException, ProtocolInvalidVersionException, ProtocolInvalidMessageException, ProtocolInvalidKeyException, ProtocolDuplicateMessageException, SelfSendException, UnsupportedDataMessageException {
        SignalServiceCipher cipher = new SignalServiceCipher(new SignalServiceAddress(accountData.username), accountData.axolotlStore, getCertificateValidator());
        try {
            return cipher.decrypt(envelope);
        } catch (ProtocolUntrustedIdentityException e) {
            // TODO We don't get the new untrusted identity from ProtocolUntrustedIdentityException anymore ... we need to get it from somewhere else
            // signalProtocolStore.saveIdentity(e.getSource(), e, TrustLevel.UNTRUSTED);
            throw e;
        }
    }

    private void handleEndSession(String source) {
        accountData.axolotlStore.deleteAllSessions(source);
    }

    public interface ReceiveMessageHandler {
        void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent decryptedContent, Throwable e);
    }

    private void handleSignalServiceDataMessage(SignalServiceDataMessage message, boolean isSync, String source, String destination, boolean ignoreAttachments) throws NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException, IOException {
        String threadId;
        if (message.getGroupInfo().isPresent()) {
            SignalServiceGroup groupInfo = message.getGroupInfo().get();
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
                                logger.warn("Failed to retrieve group avatar (" + avatar.asPointer().getId() + "): " + e.getMessage());
                            }
                        }
                    }

                    if (groupInfo.getName().isPresent()) {
                        group.name = groupInfo.getName().get();
                    }

                    if (groupInfo.getMembers().isPresent()) {
                        group.members.addAll(groupInfo.getMembers().get());
                    }

                    accountData.groupStore.updateGroup(group);
                    break;
                case DELIVER:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            logger.catching(e);
                        }
                    }
                    break;
                case QUIT:
                    if (group == null) {
                        try {
                            sendGroupInfoRequest(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            logger.catching(e);
                        }
                    } else {
                        group.members.remove(source);
                        accountData.groupStore.updateGroup(group);
                    }
                    break;
                case REQUEST_INFO:
                    if (group != null) {
                        try {
                            sendUpdateGroupMessage(groupInfo.getGroupId(), source);
                        } catch (IOException | EncapsulatedExceptions e) {
                            logger.catching(e);
                        } catch (NotAGroupMemberException e) {
                            // We have left this group, so don't send a group update message
                        }
                    }
                    break;
            }
        } else {
            if (isSync) {
                threadId = destination;
            } else {
                threadId = source;
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
                        logger.warn("Failed to retrieve attachment (" + attachment.asPointer().getId() + "): " + e.getMessage());
                    }
                }
            }
        }

        if(message.getProfileKey().isPresent() && message.getProfileKey().get().length == 32) {
            if(source.equals(accountData.username)) {
                accountData.setProfileKey(message.getProfileKey().get());
                accountData.save();
            } else {
                ContactInfo contact = accountData.contactStore.getContact(source);
                if(contact == null) {
                    contact = new ContactInfo();
                    contact.number = source;
                }
                contact.profileKey = Base64.encodeBytes(message.getProfileKey().get());
                updateContact(contact);
            }
        }
    }

    public void retryFailedReceivedMessages(ReceiveMessageHandler handler, boolean ignoreAttachments) throws NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException, UntrustedIdentityException, IOException {
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
                if (!envelope.isReceipt()) {
                    try {
                        content = decryptMessage(envelope);
                    } catch (Exception e) {
                        continue;
                    }
                    handleMessage(envelope, content, ignoreAttachments);
                }
                accountData.save();
                handler.handleMessage(envelope, content, null);
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
        this.messagePipe.shutdown();
    }

    public void receiveMessages(long timeout, TimeUnit unit, boolean returnOnTimeout, boolean ignoreAttachments, ReceiveMessageHandler handler) throws IOException {
        try {
            retryFailedReceivedMessages(handler, ignoreAttachments);
        } catch (NotAGroupMemberException | GroupNotFoundException | AttachmentInvalidException | UntrustedIdentityException e) {
            logger.catching(e);
        }
        // TODO: Do we need anything for that second-to-last argument ("listener")? signal-cli sets it to null
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.signalingKey, USER_AGENT, null, sleepTimer);

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
                                File cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
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
                        } catch (NotAGroupMemberException | GroupNotFoundException | AttachmentInvalidException | UntrustedIdentityException e) {
                            logger.catching(e);
                        }
                    }
                }
                accountData.save();
                handler.handleMessage(envelope, content, exception);
                if (exception == null || !(exception instanceof org.whispersystems.libsignal.UntrustedIdentityException)) {
                    File cacheFile = null;
                    try {
                        cacheFile = getMessageCacheFile(envelope.getSource(), now, envelope.getTimestamp());
                        Files.delete(cacheFile.toPath());
                        // Try to delete directory if empty
                        new File(getMessageCachePath()).delete();
                    } catch (IOException e) {
                        logger.warn("Failed to delete cached message file “" + cacheFile + "”: " + e.getMessage());
                    }
                }
            }
        } finally {
            if (messagePipe != null) {
                messagePipe.shutdown();
                messagePipe = null;
            }
        }
    }

    private void handleMessage(SignalServiceEnvelope envelope, SignalServiceContent content, boolean ignoreAttachments) throws NotAGroupMemberException, NotAGroupMemberException, GroupNotFoundException, AttachmentInvalidException, AttachmentInvalidException, UntrustedIdentityException, IOException {
        if (content != null) {
            if (content.getDataMessage().isPresent()) {
                SignalServiceDataMessage message = content.getDataMessage().get();
                handleSignalServiceDataMessage(message, false, envelope.getSource(), accountData.username, ignoreAttachments);
            }
            if (content.getSyncMessage().isPresent()) {
                SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();
                if (syncMessage.getSent().isPresent()) {
                    SignalServiceDataMessage message = syncMessage.getSent().get().getMessage();
                    handleSignalServiceDataMessage(message, true, envelope.getSource(), syncMessage.getSent().get().getDestination().get(), ignoreAttachments);
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
                            while ((g = s.read()) != null) {
                                GroupInfo syncGroup = accountData.groupStore.getGroup(g.getId());
                                if (syncGroup == null) {
                                    syncGroup = new GroupInfo(g.getId());
                                }
                                if (g.getName().isPresent()) {
                                    syncGroup.name = g.getName().get();
                                }
                                syncGroup.members.addAll(g.getMembers());
                                syncGroup.active = g.isActive();

                                if (g.getAvatar().isPresent()) {
                                    retrieveGroupAvatarAttachment(g.getAvatar().get(), syncGroup.groupId);
                                }
                                accountData.groupStore.updateGroup(syncGroup);
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
                                ContactInfo contact = accountData.contactStore.getContact(c.getNumber());
                                if (contact == null) {
                                    contact = new ContactInfo();
                                    contact.number = c.getNumber();
                                }
                                if (c.getName().isPresent()) {
                                    contact.name = c.getName().get();
                                }
                                if (c.getColor().isPresent()) {
                                    contact.color = c.getColor().get();
                                }

                                if(c.getProfileKey().isPresent()) {
                                    contact.profileKey = Base64.encodeBytes(c.getProfileKey().get());
                                }
                                updateContact(contact);

                                if (c.getAvatar().isPresent()) {
                                    retrieveContactAvatarAttachment(c.getAvatar().get(), contact.number);
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
                    accountData.axolotlStore.identityKeyStore.saveIdentity(verifiedMessage.getDestination(), verifiedMessage.getIdentityKey(), TrustLevel.fromVerifiedState(verifiedMessage.getVerified()));
                }
            }
        }
    }

    private SignalServiceEnvelope loadEnvelope(File file) throws IOException {
        logger.debug("Loading cached envelope from " + file.toString());
        try (FileInputStream f = new FileInputStream(file)) {
            DataInputStream in = new DataInputStream(f);
            int version = in.readInt();
            if (version > 2) {
                return null;
            }
            int type = in.readInt();
            String source = in.readUTF();
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
            return new SignalServiceEnvelope(type, source, sourceDevice, timestamp, legacyMessage, content, serverTimestamp, uuid);
}
    }

    private void storeEnvelope(SignalServiceEnvelope envelope, File file) throws IOException {
        logger.debug("Storing envelope to " + file.toString());
        try (FileOutputStream f = new FileOutputStream(file)) {
            try (DataOutputStream out = new DataOutputStream(f)) {
                out.writeInt(2); // version
                out.writeInt(envelope.getType());
                out.writeUTF(envelope.getSource());
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

    public File getContactAvatarFile(String number) {
        return new File(avatarsPath, "contact-" + number);
    }

    private File retrieveContactAvatarAttachment(SignalServiceAttachment attachment, String number) throws IOException, InvalidMessageException {
        createPrivateDirectories(avatarsPath);
        if (attachment.isPointer()) {
            SignalServiceAttachmentPointer pointer = attachment.asPointer();
            return retrieveAttachment(pointer, getContactAvatarFile(number), false);
        } else {
            SignalServiceAttachmentStream stream = attachment.asStream();
            return retrieveAttachment(stream, getContactAvatarFile(number));
        }
    }

    public File getGroupAvatarFile(byte[] groupId) {
        return new File(avatarsPath, "group-" + Base64.encodeBytes(groupId).replace("/", "_"));
    }

    private File retrieveGroupAvatarAttachment(SignalServiceAttachment attachment, byte[] groupId) throws IOException, InvalidMessageException {
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

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer) throws IOException, InvalidMessageException {
        createPrivateDirectories(attachmentsPath);
        return retrieveAttachment(pointer, getAttachmentFile(pointer.getId()), true);
    }

    private File retrieveAttachment(SignalServiceAttachmentStream stream, File outputFile) throws IOException, InvalidMessageException {
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

    private File retrieveAttachment(SignalServiceAttachmentPointer pointer, File outputFile, boolean storePreview) throws IOException, InvalidMessageException {
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

        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.signalingKey, USER_AGENT, null, sleepTimer);

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

    private InputStream retrieveAttachmentAsStream(SignalServiceAttachmentPointer pointer, File tmpFile) throws IOException, InvalidMessageException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.signalingKey, USER_AGENT, null, sleepTimer);
        return messageReceiver.retrieveAttachment(pointer, tmpFile, MAX_ATTACHMENT_SIZE);
    }

    private String canonicalizeNumber(String number) throws InvalidNumberException {
        String localNumber = accountData.username;
        return PhoneNumberFormatter.formatNumber(number, localNumber);
    }

    private SignalServiceAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = canonicalizeNumber(number);
        return new SignalServiceAddress(e164number);
    }

    public boolean isRemote() {
        return false;
    }

    private void sendGroups() throws IOException, UntrustedIdentityException {
        File groupsFile = Util.createTempFile();

        try {
            try (OutputStream fos = new FileOutputStream(groupsFile)) {
                DeviceGroupsOutputStream out = new DeviceGroupsOutputStream(fos);
                for (GroupInfo record : accountData.groupStore.getGroups()) {
                    Optional<Integer> expirationTimer = Optional.<Integer>absent();
                    Optional<String> color = Optional.<String>absent();
                    out.write(new DeviceGroup(record.groupId, Optional.fromNullable(record.name),
                            new ArrayList<>(record.members), createGroupAvatarAttachment(record.groupId),
                            record.active, expirationTimer, color, false));
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
                    if (getIdentities().containsKey(record.number)) {
                        IdentityKeyStore.Identity currentIdentity = null;
                        for (IdentityKeyStore.Identity id : getIdentities().get(record.number)) {
                            if (currentIdentity == null || id.getDateAdded().after(currentIdentity.getDateAdded())) {
                                currentIdentity = id;
                            }
                        }
                        if (currentIdentity != null) {
                            verifiedMessage = new VerifiedMessage(record.number, currentIdentity.getIdentityKey(), currentIdentity.getTrustLevel().toVerifiedState(), currentIdentity.getDateAdded().getTime());
                        }
                    }

                    // TODO include profile key
                    // TODO: Don't hard code `false` value for blocked argument
                    Optional<Integer> expirationTimer = Optional.<Integer>absent();
                    out.write(new DeviceContact(record.number, Optional.fromNullable(record.name),
                            createContactAvatarAttachment(record.number), Optional.fromNullable(record.color),
                            Optional.fromNullable(verifiedMessage), Optional.<byte[]>absent(), false, expirationTimer));
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

    private void sendVerifiedMessage(String destination, IdentityKey identityKey, TrustLevel trustLevel) throws IOException, UntrustedIdentityException {
        VerifiedMessage verifiedMessage = new VerifiedMessage(destination, identityKey, trustLevel.toVerifiedState(), System.currentTimeMillis());
        sendSyncMessage(SignalServiceSyncMessage.forVerified(verifiedMessage));
    }

    public List<ContactInfo> getContacts() {
      if(accountData.contactStore == null) {
        return Collections.<ContactInfo>emptyList();
      }
      List<ContactInfo> contacts = this.accountData.contactStore.getContacts();
      return contacts;
    }

    public ContactInfo getContact(String number) {
        return accountData.contactStore.getContact(number);
    }

    public GroupInfo getGroup(byte[] groupId) {
        return accountData.groupStore.getGroup(groupId);
    }

    public Map<String, List<IdentityKeyStore.Identity>> getIdentities() {
        return accountData.axolotlStore.identityKeyStore.getIdentities();
    }

    public List<IdentityKeyStore.Identity> getIdentities(String number) {
        return accountData.axolotlStore.identityKeyStore.getIdentities(number);
    }

    /**
     * Trust this the identity with this fingerprint
     *
     * @param name        username of the identity
     * @param fingerprint Fingerprint
     * @param level       level at with to trust the identity
     */
    public boolean trustIdentity(String name, byte[] fingerprint, TrustLevel level) throws IOException {
        List<IdentityKeyStore.Identity> ids = accountData.axolotlStore.identityKeyStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (IdentityKeyStore.Identity id : ids) {
            if (!Arrays.equals(id.getIdentityKey().serialize(), fingerprint)) {
                continue;
            }

            accountData.axolotlStore.identityKeyStore.saveIdentity(name, id.getIdentityKey(), level);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), level);
            } catch (IOException | UntrustedIdentityException e) {
                logger.catching(e);
            }
            accountData.save();
            return true;
        }
        return false;
    }

    /**
     * Trust this the identity with this safety number
     *
     * @param name         username of the identity
     * @param safetyNumber Safety number
     * @param level        level to trust the identity
     */
    public boolean trustIdentitySafetyNumber(String name, String safetyNumber, TrustLevel level) throws IOException {
        List<IdentityKeyStore.Identity> ids = accountData.axolotlStore.identityKeyStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (IdentityKeyStore.Identity id : ids) {
            if (!safetyNumber.equals(computeSafetyNumber(name, id.getIdentityKey()))) {
                continue;
            }

            accountData.axolotlStore.identityKeyStore.saveIdentity(name, id.getIdentityKey(), level);
            try {
                sendVerifiedMessage(name, id.getIdentityKey(), level);
            } catch (IOException | UntrustedIdentityException e) {
                logger.catching(e);
            }
            accountData.save();
            return true;
        }
        return false;
    }

    /**
     * Trust all keys of this identity without verification
     *
     * @param name username of the identity
     */
    public boolean trustIdentityAllKeys(String name) throws IOException {
        List<IdentityKeyStore.Identity> ids = accountData.axolotlStore.identityKeyStore.getIdentities(name);
        if (ids == null) {
            return false;
        }
        for (IdentityKeyStore.Identity id : ids) {
            if (id.getTrustLevel() == TrustLevel.UNTRUSTED) {
                accountData.axolotlStore.identityKeyStore.saveIdentity(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                try {
                    sendVerifiedMessage(name, id.getIdentityKey(), TrustLevel.TRUSTED_UNVERIFIED);
                } catch (IOException | UntrustedIdentityException e) {
                    logger.catching(e);
                }
            }
        }
        accountData.save();
        return true;
    }

    public String computeSafetyNumber(String theirUsername, IdentityKey theirIdentityKey) {
        Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(accountData.username, getIdentity(), theirUsername, theirIdentityKey);
        return fingerprint.getDisplayableFingerprint().getDisplayText();
    }

    public Optional<ContactTokenDetails> getUser(String e164number) throws IOException {
        return accountManager.getContact(e164number);
    }

    private static byte[] getTargetUnidentifiedAccessKey(SignalServiceAddress recipient) {
        // TODO implement
        return null;
    }

    public Optional<UnidentifiedAccessPair> getAccessForSync() {
        // TODO implement
        return Optional.absent();
    }

    public List<Optional<UnidentifiedAccessPair>> getAccessFor(Collection<SignalServiceAddress> recipients) {
        List<Optional<UnidentifiedAccessPair>> result = new ArrayList<>(recipients.size());
        for (SignalServiceAddress recipient : recipients) {
            result.add(Optional.<UnidentifiedAccessPair>absent());
        }
        return result;
    }

    public Optional<UnidentifiedAccessPair> getAccessFor(SignalServiceAddress recipient) {
        // TODO implement
        return Optional.absent();
    }

    public byte[] getProfileKey() throws IOException {
        return accountData.getProfileKey();
    }

    public void setProfileKey(final byte[] profileKey) throws IOException {
        accountData.setProfileKey(profileKey);
	    accountData.save();
    }

    public void setProfileName(String name) throws IOException {
        accountManager.setProfileName(accountData.getProfileKey(), name);
	    accountData.save();
    }

    public SignalServiceProfile getProfile(String number) throws IOException {
        final SignalServiceMessageReceiver messageReceiver = new SignalServiceMessageReceiver(serviceConfiguration, accountData.username, accountData.password, accountData.deviceId, accountData.signalingKey, USER_AGENT, null, sleepTimer);
        return messageReceiver.retrieveProfile(new SignalServiceAddress(number), Optional.<UnidentifiedAccess>absent());
    }
}
