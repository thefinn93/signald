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

package io.finn.signald;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.clientprotocol.MessageEncoder;
import io.finn.signald.clientprotocol.v0.JsonAddress;
import io.finn.signald.clientprotocol.v0.JsonMessageEnvelope;
import io.finn.signald.clientprotocol.v0.JsonSendMessageResult;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.db.RecipientsTable;
import io.finn.signald.db.ServersTable;
import io.finn.signald.exceptions.*;
import io.finn.signald.storage.AccountData;
import io.finn.signald.storage.Group;
import io.finn.signald.storage.GroupInfo;
import io.finn.signald.storage.ProfileAndCredentialEntry;
import io.finn.signald.util.GroupsUtil;
import io.finn.signald.util.JSONUtil;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;
import org.asamk.signal.TrustLevel;
import org.asamk.signal.util.Hex;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.groupsv2.GroupLinkNotActiveException;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.util.Base64;

public class LegacySocketHandler {
  private BufferedReader reader;
  private PrintWriter writer;
  private ObjectMapper mpr = new ObjectMapper();
  private static final Logger logger = LogManager.getLogger();
  private Socket socket;

  public LegacySocketHandler(Socket socket) throws IOException {
    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.writer = new PrintWriter(socket.getOutputStream(), true);
    this.socket = socket;

    this.mpr.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
    this.mpr.setSerializationInclusion(Include.NON_NULL);
    this.mpr.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.mpr.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  public void handleRequest(JsonRequest request) throws Throwable {
    switch (request.type) {
    case "send":
      send(request);
      break;
    case "typing_started":
      typing(request, SignalServiceTypingMessage.Action.STARTED);
      break;
    case "typing_stopped":
      typing(request, SignalServiceTypingMessage.Action.STOPPED);
      break;
    case "mark_delivered":
      markDelivered(request);
      break;
    case "mark_read":
      markRead(request);
      break;
    case "subscribe":
      subscribe(request);
      break;
    case "unsubscribe":
      unsubscribe(request);
      break;
    case "list_accounts":
      listAccounts(request);
      break;
    case "register":
      register(request);
      break;
    case "verify":
      verify(request);
      break;
    case "link":
      link(request);
      break;
    case "add_device":
      addDevice(request);
      break;
    case "update_group":
      updateGroup(request);
      break;
    case "set_expiration":
      setExpiration(request);
      break;
    case "list_groups":
      listGroups(request);
      break;
    case "leave_group":
      leaveGroup(request);
      break;
    case "get_user":
      getUser(request);
      break;
    case "get_identities":
      getIdentities(request);
      break;
    case "trust":
      trust(request);
      break;
    case "sync_contacts":
      syncContacts(request);
      break;
    case "sync_groups":
      syncGroups(request);
      break;
    case "sync_configuration":
      syncConfiguration(request);
      break;
    case "list_contacts":
      listContacts(request);
      break;
    case "update_contact":
      updateContact(request);
      break;
    case "get_profile":
      getProfile(request);
      break;
    case "set_profile":
      setProfile(request);
      break;
    case "react":
      react(request);
      break;
    case "refresh_account":
      refreshAccount(request);
      break;
    case "group_link_info":
      groupLinkInfo(request);
      break;
    default:
      logger.warn("Unknown command type " + request.type);
      this.reply("unknown_command", new JsonStatusMessage(5, "Unknown command type " + request.type, request), request.id);
      break;
    }
  }

  private void send(JsonRequest request) throws IOException, AttachmentInvalidException, NoSuchAccountException, UnknownGroupException, SQLException,
                                                io.finn.signald.exceptions.InvalidRecipientException, InvalidKeyException, ServerNotFoundException, InvalidProxyException,
                                                NoSendPermissionException {
    Manager manager = Manager.get(request.username);

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();

    if (request.messageBody != null) {
      messageBuilder = messageBuilder.withBody(request.messageBody);
    }

    if (request.attachments != null) {
      List<SignalServiceAttachment> attachments = new ArrayList<>(request.attachments.size());
      for (JsonAttachment attachment : request.attachments) {
        try {
          File attachmentFile = new File(attachment.filename);
          InputStream attachmentStream = new FileInputStream(attachmentFile);
          final long attachmentSize = attachmentFile.length();
          if (attachment.contentType == null) {
            attachment.contentType = Files.probeContentType(attachmentFile.toPath());
            if (attachment.contentType == null) {
              attachment.contentType = "application/octet-stream";
            }
          }
          String customFilename = attachmentFile.getName();
          if (attachment.customFilename != null) {
            customFilename = attachment.customFilename;
          }
          attachments.add(new SignalServiceAttachmentStream(attachmentStream, attachment.contentType, attachmentSize, Optional.of(customFilename), attachment.voiceNote, false,
                                                            false, attachment.getPreview(), attachment.width, attachment.height, System.currentTimeMillis(),
                                                            Optional.fromNullable(attachment.caption), Optional.fromNullable(attachment.blurhash), null, null, Optional.absent()));
        } catch (IOException e) {
          throw new AttachmentInvalidException(attachment.filename, e);
        }
      }
      messageBuilder.withAttachments(attachments);
    }

    if (request.quote != null) {
      messageBuilder.withQuote(request.quote.getQuote());
    }

    if (request.reaction != null) {
      messageBuilder.withReaction(request.reaction.getReaction());
    }

    if (request.timestamp != null) {
      messageBuilder.withTimestamp(request.timestamp);
    }

    Recipient recipient = request.recipientAddress == null ? null : manager.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    handleSendMessage(manager.send(messageBuilder, recipient, request.recipientGroupId), request);
  }

  private void typing(JsonRequest request, SignalServiceTypingMessage.Action action)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    logger.info("Typing");
    Manager m = Manager.get(request.username);

    byte[] groupId = null;
    if (request.recipientGroupId != null) {
      groupId = Base64.decode(request.recipientGroupId);
    }
    if (groupId == null) {
      groupId = new byte[0];
    }

    if (request.when == 0) {
      request.when = System.currentTimeMillis();
    }

    SignalServiceTypingMessage message = new SignalServiceTypingMessage(action, request.when, Optional.fromNullable(groupId));

    Recipient recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    SendMessageResult result = m.sendTypingMessage(message, recipient);
    if (result != null) {
      SendMessageResult.IdentityFailure identityFailure = result.getIdentityFailure();
      if (identityFailure != null) {
        Recipient r = m.getRecipientsTable().get(result.getAddress());
        this.reply("untrusted_identity", new JsonUntrustedIdentityException(identityFailure.getIdentityKey(), r, m, request), request.id);
      }
    }
  }

  private void markDelivered(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    logger.info("Mark as Delivered");
    Manager m = Manager.get(request.username);

    if (request.when == 0) {
      request.when = System.currentTimeMillis();
    }

    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.DELIVERY, request.timestamps, request.when);

    Recipient recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    SendMessageResult result = m.sendReceipt(message, recipient);
    if (result != null) {
      SendMessageResult.IdentityFailure identityFailure = result.getIdentityFailure();
      if (identityFailure != null) {
        Recipient r = m.getRecipientsTable().get(result.getAddress());
        this.reply("untrusted_identity", new JsonUntrustedIdentityException(identityFailure.getIdentityKey(), r, m, request), request.id);
      }
    }
  }

  private void markRead(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    logger.info("Mark as Read");
    Manager m = Manager.get(request.username);

    if (request.when == 0) {
      request.when = System.currentTimeMillis();
    }

    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, request.timestamps, request.when);
    Recipient recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    SendMessageResult result = m.sendReceipt(message, recipient);
    if (result == null) {
      this.reply("mark_read", null, request.id);
    } else {
      SendMessageResult.IdentityFailure identityFailure = result.getIdentityFailure();
      if (identityFailure != null) {
        Recipient r = m.getRecipientsTable().get(result.getAddress());
        this.reply("untrusted_identity", new JsonUntrustedIdentityException(identityFailure.getIdentityKey(), r, m, request), request.id);
      }
    }
  }

  private void listAccounts(JsonRequest request) throws IOException, SQLException, NoSuchAccountError {
    JsonAccountList accounts = new JsonAccountList();
    this.reply("account_list", accounts, request.id);
  }

  private void register(JsonRequest request) throws IOException, InvalidInputException, SQLException, ServerNotFoundException, InvalidProxyException {
    logger.info("Register request: " + request);
    RegistrationManager m = RegistrationManager.get(request.username, UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID));
    boolean voice = false;
    if (request.voice != null) {
      voice = request.voice;
    }

    logger.info("Registering (voice: " + voice + ")");
    try {
      m.register(voice, Optional.fromNullable(request.captcha), ServersTable.DEFAULT_SERVER);
      this.reply("verification_required", new JsonAccount(m), request.id);
    } catch (CaptchaRequiredException e) {
      this.reply("captcha_required", "see https://signald.org/articles/captcha/", request.id);
    }
  }

  private void verify(JsonRequest request)
      throws IOException, InvalidInputException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, NoSuchAccountException, NoSuchAccountError {
    RegistrationManager rm = RegistrationManager.get(request.username, UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID));
    if (!rm.hasPendingKeys()) {
      logger.warn("User has no keys, first call register.");
      this.reply("error", "user has no keys, must register first", request.id);
    } else if (rm.isRegistered()) {
      logger.warn("User is already verified");
      this.reply("error", "user is already verified", request.id);
    } else {
      logger.info("Submitting verification code " + request.code + " for number " + request.username);
      try {
        Manager m = rm.verifyAccount(request.code);
        this.reply("verification_succeeded", new JsonAccount(m.getAccount()), request.id);
      } catch (LockedException e) {
        logger.warn("Failed to register phone number with PIN lock. See https://gitlab.com/signald/signald/-/issues/47");
        this.reply("error", "registering phone numbers with a PIN lock is not currently supported, see https://gitlab.com/signald/signald/-/issues/47", request.id);
      }
    }
  }

  private void addDevice(JsonRequest request) throws IOException, InvalidKeyException, AssertionError, URISyntaxException, NoSuchAccountException, InvalidInputException,
                                                     SQLException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    m.addDeviceLink(new URI(request.uri));
    reply("device_added", new JsonStatusMessage(4, "Successfully linked device"), request.id);
  }

  private void updateGroup(JsonRequest request) throws IOException, GroupNotFoundException, NotAGroupMemberException, NoSuchAccountException, VerificationFailedException,
                                                       InvalidGroupStateException, InterruptedException, ExecutionException, TimeoutException, UnknownGroupException, SQLException,
                                                       InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);

    byte[] groupId = null;
    if (request.recipientGroupId != null) {
      groupId = Base64.decode(request.recipientGroupId);
    }

    if (groupId == null) {
      groupId = new byte[0];
    }

    String groupName = request.groupName;
    if (groupName == null) {
      groupName = "";
    }

    List<String> groupMembers = request.members;
    if (groupMembers == null) {
      groupMembers = new ArrayList<>();
    }

    String groupAvatar = request.avatar;
    if (groupAvatar == null) {
      groupAvatar = "";
    }

    if (request.recipientGroupId.length() == 44) { // v2 group
      GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();
      Group group = groupsV2Manager.getGroup(request.recipientGroupId);
      RecipientsTable recipientsTable = m.getRecipientsTable();
      List<SignalServiceAddress> currentMembers =
          group.group.getMembersList().stream().map(x -> new SignalServiceAddress(UuidUtil.fromByteString(x.getUuid()))).collect(Collectors.toList());
      List<Recipient> recipients = recipientsTable.get(currentMembers);
      Pair<SignalServiceDataMessage.Builder, Group> output;
      if (request.groupName != null) {
        output = groupsV2Manager.updateTitle(request.recipientGroupId, request.groupName);
      } else if (request.members != null) {
        List<ProfileAndCredentialEntry> members = new ArrayList<>();
        for (String member : request.members) {
          Recipient recipient = recipientsTable.get(member, null);
          ProfileAndCredentialEntry profileAndCredentialEntry = m.getRecipientProfileKeyCredential(recipient);
          members.add(profileAndCredentialEntry);
          recipients.add(recipient);
        }
        output = groupsV2Manager.addMembers(request.recipientGroupId, members);
      } else if (request.avatar != null) {
        output = groupsV2Manager.updateAvatar(request.recipientGroupId, request.avatar);
      } else {
        this.reply("group_update_error", "unknown action for v2 group. only name changes and membership adds are supported in this version of the update_group request.",
                   request.id);
        return;
      }
      m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2(), recipients);

      AccountData accountData = m.getAccountData();
      accountData.groupsV2.update(output.second());
      accountData.save();
    } else {
      GroupInfo group = m.updateGroup(groupId, groupName, groupMembers, groupAvatar);
      if (groupId.length != group.groupId.length) {
        this.reply("group_created", new JsonStatusMessage(5, "Created new group " + group.name + "."), request.id);
      } else {
        this.reply("group_updated", new JsonStatusMessage(6, "Updated group"), request.id);
      }
    }
  }

  private void setExpiration(JsonRequest request) throws IOException, GroupNotFoundException, NotAGroupMemberException, NoSuchAccountException, UnknownGroupException,
                                                         VerificationFailedException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    List<SendMessageResult> results;
    if (request.recipientGroupId != null) {
      if (request.recipientGroupId.length() == 44) {
        Pair<SignalServiceDataMessage.Builder, Group> output = m.getGroupsV2Manager().updateGroupTimer(request.recipientGroupId, request.expiresInSeconds);
        results = m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2());
        m.getAccountData().groupsV2.update(output.second());
        m.getAccountData().save();
      } else {
        byte[] groupId = Base64.decode(request.recipientGroupId);
        results = m.setExpiration(groupId, request.expiresInSeconds);
      }
    } else {
      Recipient recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
      results = m.setExpiration(recipient, request.expiresInSeconds);
    }

    handleSendMessage(results, request);
    this.reply("expiration_updated", null, request.id);
  }

  private void listGroups(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    this.reply("group_list", new JsonGroupList(m), request.id);
  }

  private void leaveGroup(JsonRequest request) throws IOException, GroupNotFoundException, NotAGroupMemberException, NoSuchAccountException, UnknownGroupException,
                                                      VerificationFailedException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    if (request.recipientGroupId.length() == 44) {
      AccountData accountData = m.getAccountData();
      Group group = accountData.groupsV2.get(request.recipientGroupId);
      if (group == null) {
        reply("leave_group_error", "group not found", request.id);
        return;
      }

      List<SignalServiceAddress> members = group.group.getMembersList().stream().map(GroupsUtil::getMemberAddress).collect(Collectors.toList());
      List<Recipient> recipients = m.getRecipientsTable().get(members);

      GroupsV2Manager groupsV2Manager = m.getGroupsV2Manager();
      Pair<SignalServiceDataMessage.Builder, Group> output = groupsV2Manager.leaveGroup(request.recipientGroupId);
      m.sendGroupV2Message(output.first(), output.second().getSignalServiceGroupV2(), recipients);
      accountData.groupsV2.remove(output.second());
      accountData.save();
    } else {
      byte[] groupId = Base64.decode(request.recipientGroupId);
      m.sendQuitGroupMessage(groupId);
    }
    this.reply("left_group", new JsonStatusMessage(7, "Successfully left group"), request.id);
  }

  private void reply(String type, Object data, String id) throws JsonProcessingException {
    JsonMessageWrapper message = new JsonMessageWrapper(type, data, id);
    String jsonmessage = this.mpr.writeValueAsString(message);
    PrintWriter out = new PrintWriter(this.writer, true);
    out.println(jsonmessage);
  }

  private void link(JsonRequest request) throws AssertionError, IOException, InvalidKeyException, URISyntaxException, NoSuchAccountException, InvalidInputException, SQLException,
                                                ServerNotFoundException, InvalidProxyException, NoSuchAccountError, UntrustedIdentityException {
    ProvisioningManager pm = new ProvisioningManager(UUID.fromString(BuildConfig.DEFAULT_SERVER_UUID));
    String deviceName = "signald"; // TODO: Set this to "signald on <hostname>" or maybe allow client to specify
    if (request.deviceName != null) {
      deviceName = request.deviceName;
    }
    try {
      logger.info("Generating linking URI");
      URI uri = pm.getDeviceLinkUri();
      this.reply("linking_uri", new JsonLinkingURI(uri), request.id);
      UUID account = pm.finishDeviceLink(deviceName, false);
      Manager m = Manager.get(account);
      this.reply("linking_successful", new JsonAccount(m.getAccount()), request.id);
    } catch (TimeoutException e) {
      this.reply("linking_error", new JsonStatusMessage(1, "Timed out while waiting for device to link", request), request.id);
    } catch (UserAlreadyExistsException e) {
      this.reply("linking_error", new JsonStatusMessage(3, e.getMessage(), request), request.id);
    }
  }

  private void getUser(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    Optional<ContactTokenDetails> contact = m.getUser(request.recipientAddress.number);
    if (contact.isPresent()) {
      this.reply("user", new JsonContactTokenDetails(contact.get()), request.id);
    } else {
      this.reply("user_not_registered", null, request.id);
    }
  }

  private void getIdentities(JsonRequest request)
      throws IOException, NoSuchAccountException, SQLException, InvalidAddressException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    Recipient recipient = null;
    if (request.recipientAddress != null) {
      recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    }
    this.reply("identities", new JsonIdentityList(recipient, m), request.id);
  }

  private void trust(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    TrustLevel trustLevel = TrustLevel.TRUSTED_VERIFIED;
    if (request.fingerprint == null) {
      this.reply("input_error", new JsonStatusMessage(0, "Fingerprint must be a string!", request), request.id);
      return;
    }
    if (request.trustLevel != null) {
      try {
        trustLevel = TrustLevel.valueOf(request.trustLevel.toUpperCase());
      } catch (IllegalArgumentException e) {
        this.reply("input_error", new JsonStatusMessage(0, "Invalid TrustLevel", request), request.id);
        return;
      }
    }
    String fingerprint = request.fingerprint.replaceAll(" ", "");
    RecipientsTable recipientsTable = m.getRecipientsTable();
    if (fingerprint.length() == 66) {
      byte[] fingerprintBytes;
      fingerprintBytes = Hex.toByteArray(fingerprint.toLowerCase(Locale.ROOT));
      boolean res = m.trustIdentity(recipientsTable.get(request.recipientAddress), fingerprintBytes, trustLevel);
      if (!res) {
        this.reply("trust_failed",
                   new JsonStatusMessage(0, "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.", request),
                   request.id);
      } else {
        this.reply("trusted_fingerprint", new JsonStatusMessage(0, "Successfully trusted fingerprint", request), request.id);
      }
    } else if (fingerprint.length() == 60) {
      boolean res = m.trustIdentitySafetyNumber(recipientsTable.get(request.recipientAddress), fingerprint, trustLevel);
      if (!res) {
        this.reply("trust_failed",
                   new JsonStatusMessage(0, "Failed to set the trust for the safety number of this number, make sure the number and the safety number are correct.", request),
                   request.id);
      } else {
        this.reply("trusted_safety_number", new JsonStatusMessage(0, "Successfully trusted safety number", request), request.id);
      }
    } else {
      System.err.println("Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number");
      this.reply("trust_failed", new JsonStatusMessage(0, "Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number", request), request.id);
    }
  }

  private void syncContacts(JsonRequest request)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException {
    Manager m = Manager.get(request.username);
    m.requestSyncContacts();
    this.reply("sync_requested", null, request.id);
  }

  private void syncGroups(JsonRequest request)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException {
    Manager m = Manager.get(request.username);
    m.requestSyncGroups();
    this.reply("sync_requested", null, request.id);
  }

  private void syncConfiguration(JsonRequest request)
      throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException, UntrustedIdentityException {
    Manager m = Manager.get(request.username);
    m.requestSyncConfiguration();
    this.reply("sync_requested", null, request.id);
  }

  private void listContacts(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    this.reply("contact_list", m.getContacts(), request.id);
  }

  public void updateContact(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    if (request.contact == null) {
      this.reply("update_contact_error", new JsonStatusMessage(0, "No contact specificed!", request), request.id);
      return;
    }

    if (request.contact.address == null) {
      this.reply("update_contact_error", new JsonStatusMessage(0, "No address specified! Contact must have an address", request), request.id);
      return;
    }

    m.updateContact(request.contact);
    this.reply("contact_updated", null, request.id);
  }

  private void subscribe(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    UUID account = AccountsTable.getUUID(request.username);
    MessageReceiver.subscribe(account, new LegacyMessageEncoder(socket, request.username));
    this.reply("subscribed", null, request.id);
  }

  private void unsubscribe(JsonRequest request) throws IOException, SQLException, NoSuchAccountException {
    UUID account = AccountsTable.getUUID(request.username);
    MessageReceiver.unsubscribe(account, socket);
    this.reply("unsubscribed", null, request.id);
  }

  private void getProfile(JsonRequest request) throws IOException, NoSuchAccountException, InterruptedException, ExecutionException, TimeoutException, SQLException,
                                                      InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    Recipient recipient = m.getRecipientsTable().get(request.recipientAddress.number, request.recipientAddress.getUUID());
    ProfileAndCredentialEntry profileEntry = m.getRecipientProfileKeyCredential(recipient);
    if (profileEntry == null) {
      this.reply("profile_not_available", new JsonAddress(recipient.getAddress()), request.id);
      return;
    }
    m.getAccountData().saveIfNeeded();
    SignalServiceProfile profile = m.getSignalServiceProfile(recipient, profileEntry.getProfileKey());
    this.reply("profile", new JsonProfile(profile, profileEntry.getProfileKey(), request.recipientAddress), request.id);
  }

  private void setProfile(JsonRequest request)
      throws IOException, NoSuchAccountException, InvalidInputException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    m.setProfile(request.name, null);
    this.reply("profile_set", null, request.id);
  }

  private void react(JsonRequest request) throws IOException, NoSuchAccountException, InvalidRecipientException, UnknownGroupException, SQLException, InvalidKeyException,
                                                 ServerNotFoundException, InvalidProxyException, NoSendPermissionException {
    Manager manager = Manager.get(request.username);
    RecipientsTable recipientsTable = manager.getRecipientsTable();
    Recipient recipient = null;
    if (request.recipientAddress != null) {
      recipient = recipientsTable.get(request.recipientAddress.number, request.recipientAddress.getUUID());
    }

    Recipient targetAuthor = recipientsTable.get(request.reaction.targetAuthor.number, request.reaction.targetAuthor.getUUID());
    request.reaction.targetAuthor = new io.finn.signald.clientprotocol.v1.JsonAddress(targetAuthor.getAddress());

    SignalServiceDataMessage.Builder messageBuilder = SignalServiceDataMessage.newBuilder();
    messageBuilder.withReaction(request.reaction.getReaction());
    handleSendMessage(manager.send(messageBuilder, recipient, request.recipientGroupId), request);
  }

  private void refreshAccount(JsonRequest request) throws IOException, NoSuchAccountException, SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    m.refreshAccount();
    this.reply("account_refreshed", null, request.id);
  }

  private void groupLinkInfo(JsonRequest request) throws IOException, NoSuchAccountException, InvalidInputException, VerificationFailedException, GroupLinkNotActiveException,
                                                         SQLException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    Manager m = Manager.get(request.username);
    GroupsV2Manager groupsv2Manager = m.getGroupsV2Manager();
    this.reply("group_join_info", groupsv2Manager.getGroupJoinInfo(request.uri), request.id);
  }

  private void handleSendMessage(List<SendMessageResult> sendMessageResults, JsonRequest request) throws JsonProcessingException {
    List<JsonSendMessageResult> results = new ArrayList<>();
    for (SendMessageResult r : sendMessageResults) {
      results.add(new JsonSendMessageResult(r));
    }
    this.reply("send_results", results, request.id);
  }

  static class LegacyMessageEncoder implements MessageEncoder {
    private final ObjectMapper mapper = JSONUtil.GetMapper();
    private final Socket socket;
    private final String accountE164;
    private final UUID accountUUID;

    LegacyMessageEncoder(Socket socket, String accountE164) throws SQLException, NoSuchAccountException {
      this.socket = socket;
      this.accountE164 = accountE164;
      this.accountUUID = AccountsTable.getUUID(accountE164);
    }

    private void broadcast(JsonMessageWrapper o) throws IOException {
      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      out.println(mapper.writeValueAsString(o));
    }
    @Override
    public void broadcastIncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content) throws IOException {
      if (!shouldBroadcast(content)) {
        return;
      }
      try {
        JsonMessageEnvelope e = new JsonMessageEnvelope(envelope, content, accountUUID);
        broadcast(new JsonMessageWrapper("message", e));
      } catch (NoSuchAccountException | SQLException | InvalidKeyException | ServerNotFoundException | InvalidProxyException e) {
        logger.warn("Unexpected exception while broadcasting incoming message: " + e.toString());
      }
    }

    @Override
    public void broadcastReceiveFailure(Throwable exception) throws IOException {
      if (exception instanceof org.whispersystems.libsignal.UntrustedIdentityException) {
        JsonUntrustedIdentityException message = new JsonUntrustedIdentityException((org.whispersystems.libsignal.UntrustedIdentityException)exception, accountE164);
        broadcast(new JsonMessageWrapper("inbound_identity_failure", message));
      } else {
        broadcast(new JsonMessageWrapper("unreadable_message", null, exception));
      }
    }

    @Override
    public void broadcastListenStarted() throws IOException {
      broadcast(new JsonMessageWrapper("listen_started", accountE164, (String)null));
    }

    @Override
    public void broadcastListenStopped(Throwable exception) throws IOException {
      broadcast(new JsonMessageWrapper("listener_stopped", accountE164, exception));
    }

    @Override
    public boolean isClosed() {
      return socket.isClosed();
    }

    @Override
    public boolean equals(Socket s) {
      return socket.equals(s);
    }

    private boolean shouldBroadcast(SignalServiceContent content) {
      if (content == null) {
        return true;
      }
      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage dataMessage = content.getDataMessage().get();
        if (dataMessage.getGroupContext().isPresent()) {
          SignalServiceGroupContext group = dataMessage.getGroupContext().get();
          return group.getGroupV1Type() != SignalServiceGroup.Type.REQUEST_INFO;
        }
      }
      return true;
    }
  }
}
