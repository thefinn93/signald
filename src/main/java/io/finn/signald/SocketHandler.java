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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.storage.ContactInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asamk.signal.*;
import org.asamk.signal.util.Hex;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.VerificationFailedException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.*;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.util.Base64;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class SocketHandler implements Runnable {
  private BufferedReader reader;
  private PrintWriter writer;
  private ConcurrentHashMap<String,MessageReceiver> receivers;
  private ObjectMapper mpr = new ObjectMapper();
  private static final Logger logger = LogManager.getLogger();
  private Socket socket;
  private ArrayList<String> subscribedAccounts = new ArrayList<>();

  public SocketHandler(Socket socket, ConcurrentHashMap<String,MessageReceiver> receivers) throws IOException {
    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.writer = new PrintWriter(socket.getOutputStream(), true);
    this.socket = socket;
    this.receivers = receivers;

    this.mpr.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
    this.mpr.setSerializationInclusion(Include.NON_NULL);
    this.mpr.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.mpr.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  public void run() {
    logger.info("Client connected");

    try {
      reply("version", new JsonVersionMessage(), null);

      while (true) {
        final String line = reader.readLine();

        /* client disconnected */
        if (line == null) {
          logger.info("Client disconnected");
          break;
        }

        /* client sent whitespace -- ignore */
        if (line.trim().length() == 0) {
          continue;
        }

        logger.debug(line);

        JsonRequest request = null;

        try {
          request = mpr.readValue(line, JsonRequest.class);
          handleRequest(request);
        } catch(JsonProcessingException e) {
          handleError(e, null);
        } catch (Throwable e) {
          handleError(e, request);
        }
      }

    } catch(IOException e) {
      handleError(e, null);
    } finally {

      try {
        reader.close();
        writer.close();
      } catch (IOException e) {
        logger.catching(e);
      }

      for(Map.Entry<String, MessageReceiver> entry : receivers.entrySet()) {
        if(entry.getValue().unsubscribe(socket)) {
          logger.info("Unsubscribed from " + entry.getKey());
          receivers.remove(entry.getKey());
        }
      }
    }
  }

  private void handleRequest(JsonRequest request) throws Throwable {
    switch(request.type) {
      case "send":
        send(request);
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
      case "list_contacts":
        listContacts(request);
        break;
      case "update_contact":
        updateContact(request);
        break;
      case "version":
        version();
        break;
      case "get_profile":
        getProfile(request);
        break;
      case "set_profile":
        setProfile(request);
        break;
      default:
        logger.warn("Unknown command type " + request.type);
        this.reply("unknown_command", new JsonStatusMessage(5, "Unknown command type " + request.type, request), request.id);
        break;
    }
  }

  private void send(JsonRequest request) throws IOException, UntrustedIdentityException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, NoSuchAccountException {
    Manager manager = Manager.get(request.username);

    SignalServiceDataMessage.Quote quote = null;

    if(request.quote != null) {
      quote = request.quote.getQuote();
    }

    if(request.attachmentFilenames != null) {
      logger.warn("Using deprecated attachmentFilenames argument for send! Use attachments instead");
      if(request.attachments == null) {
        request.attachments = new ArrayList<>();
      }
      for(String attachmentFilename: request.attachmentFilenames) {
        request.attachments.add(new JsonAttachment(attachmentFilename));
      }
    }

    List<SignalServiceAttachment> attachments = null;
    if (request.attachments != null) {
        attachments = new ArrayList<>(request.attachments.size());
        for (JsonAttachment attachment : request.attachments) {
            try {
                File attachmentFile = new File(attachment.filename);
                InputStream attachmentStream = new FileInputStream(attachmentFile);
                final long attachmentSize = attachmentFile.length();
                String mime = Files.probeContentType(attachmentFile.toPath());
                if (mime == null) {
                    mime = "application/octet-stream";
                }

                attachments.add(new SignalServiceAttachmentStream(attachmentStream, mime, attachmentSize, Optional.of(attachmentFile.getName()), attachment.voiceNote, attachment.getPreview(), attachment.width, attachment.height, System.currentTimeMillis(), Optional.fromNullable(attachment.caption), Optional.fromNullable(attachment.blurhash), null, null, Optional.absent()));
            } catch (IOException e) {
                throw new AttachmentInvalidException(attachment.filename, e);
            }
        }
    }

    try {
      List<SendMessageResult> sendMessageResults;
      if(request.recipientGroupId != null) {
        byte[] groupId = Base64.decode(request.recipientGroupId);
        sendMessageResults = manager.sendGroupMessage(request.messageBody, attachments, groupId, quote);
      } else {
        sendMessageResults = manager.sendMessage(request.messageBody, attachments, request.recipientNumber, quote);
      }

      for(SendMessageResult result: sendMessageResults) {
        SendMessageResult.Success success = result.getSuccess();
        if(success != null) {
          if(success.isUnidentified()) {
            this.reply("success", new JsonStatusMessage(0, "successfully send unidentified message"), request.id);
          }
          if(success.isNeedsSync()) {
            this.reply("success", new JsonStatusMessage(1, "isNeedsSync = true"), request.id);
          }
        }

        if(result.isNetworkFailure()) {
          // TODO: Log more info about what message failed, who it failed to, and any other info needed to resend
          this.reply("network_failure", null, request.id);
        }

        if(result.isUnregisteredFailure()) {
          this.reply("unregistered_user", null, request.id);
        }

        SendMessageResult.IdentityFailure identityFailure = result.getIdentityFailure();
        if(identityFailure != null) {
          this.reply("untrusted_identity", new JsonUntrustedIdentityException(identityFailure.getIdentityKey(), result.getAddress().getLegacyIdentifier(), manager, request), request.id);
        }

      }
    } catch(EncapsulatedExceptions e) {
      for(UnregisteredUserException i: e.getUnregisteredUserExceptions()) {
        this.reply("unregistered_user", new JsonUnregisteredUserException(i), request.id);
      }

      for(NetworkFailureException i: e.getNetworkExceptions()) {
        this.reply("network_failure", new JsonNetworkFailureException(i), request.id);
      }
    }
  }

  private void markRead(JsonRequest request) throws IOException, NoSuchAccountException {
    logger.info("Mark as Read");
    Manager m = Manager.get(request.username);

    if(request.when == 0) {
      request.when = System.currentTimeMillis();
    }

    SignalServiceReceiptMessage message = new SignalServiceReceiptMessage(
        SignalServiceReceiptMessage.Type.READ,
        request.timestamps,
        request.when);

    SendMessageResult result = m.sendReceipt(message, request.recipientNumber);
    if(result != null) {
      SendMessageResult.IdentityFailure identityFailure = result.getIdentityFailure();
      if(identityFailure != null) {
        this.reply("untrusted_identity", new JsonUntrustedIdentityException(identityFailure.getIdentityKey(), result.getAddress().getLegacyIdentifier(), m, request), request.id);
      }
    }
  }

  private void listAccounts(JsonRequest request) throws IOException {
    JsonAccountList accounts = new JsonAccountList(subscribedAccounts);
    this.reply("account_list", accounts, request.id);
  }

  private void register(JsonRequest request) throws IOException, NoSuchAccountException, InvalidInputException {
    logger.info("Register request: " + request);
    Manager m = Manager.get(request.username, true);
    Boolean voice = false;
    if(request.voice != null) {
      voice = request.voice;
    }

    if(!m.userHasKeys()) {
      logger.info("User has no keys, making some");
      m.createNewIdentity();
    }
    logger.info("Registering (voice: " + voice + ")");
    m.register(voice, Optional.fromNullable(request.captcha));
    this.reply("verification_required", new JsonAccount(m), request.id);
  }

  private void verify(JsonRequest request) throws IOException, NoSuchAccountException, InvalidInputException {
    Manager m = Manager.get(request.username);
    if(!m.userHasKeys()) {
      logger.warn("User has no keys, first call register.");
    } else if(m.isRegistered()) {
      logger.warn("User is already verified");
    } else {
      logger.info("Submitting verification code " + request.code + " for number " + request.username);
      m.verifyAccount(request.code);
      this.reply("verification_succeeded", new JsonAccount(m), request.id);
    }
  }

  private void addDevice(JsonRequest request) throws IOException, InvalidKeyException, AssertionError, URISyntaxException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    m.addDeviceLink(new URI(request.uri));
    reply("device_added", new JsonStatusMessage(4, "Successfully linked device"), request.id);
  }

  private void updateGroup(JsonRequest request) throws IOException, EncapsulatedExceptions, UntrustedIdentityException, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException, NoSuchAccountException {
    Manager m = Manager.get(request.username);

    byte[] groupId = null;
    if(request.recipientGroupId != null) {
      groupId = Base64.decode(request.recipientGroupId);
    }
    if (groupId == null) {
        groupId = new byte[0];
    }

    String groupName = request.groupName;
    if(groupName == null) {
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

    byte[] newGroupId = m.updateGroup(groupId, groupName, groupMembers, groupAvatar);

    if (groupId.length != newGroupId.length) {
        this.reply("group_created", new JsonStatusMessage(5, "Created new group " + groupName + "."), request.id);
    } else {
        this.reply("group_updated", new JsonStatusMessage(6, "Updated group"), request.id);
    }
  }

  private void setExpiration(JsonRequest request) throws IOException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException, UntrustedIdentityException, EncapsulatedExceptions, NoSuchAccountException {
    Manager m = Manager.get(request.username);

    if(request.recipientGroupId != null) {
      byte[] groupId = Base64.decode(request.recipientGroupId);
      m.setExpiration(groupId, request.expiresInSeconds);
    } else {
      m.setExpiration(request.recipientNumber, request.expiresInSeconds);
    }

    this.reply("expiration_updated", null, request.id);
  }

  private void listGroups(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    this.reply("group_list", new JsonGroupList(m), request.id);
  }

  private void leaveGroup(JsonRequest request) throws IOException, GroupNotFoundException, UntrustedIdentityException, NotAGroupMemberException, EncapsulatedExceptions, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    byte[] groupId = Base64.decode(request.recipientGroupId);
    m.sendQuitGroupMessage(groupId);
    this.reply("left_group", new JsonStatusMessage(7, "Successfully left group"), request.id);
  }

  private void reply(String type, Object data, String id) throws JsonProcessingException {
    JsonMessageWrapper message = new JsonMessageWrapper(type, data, id);
    String jsonmessage = this.mpr.writeValueAsString(message);
    PrintWriter out = new PrintWriter(this.writer, true);
    out.println(jsonmessage);
  }


  private void link(JsonRequest request) throws AssertionError, IOException, InvalidKeyException, InvalidInputException {
    Manager m = new Manager(null);
    m.createNewIdentity();
    String deviceName = "signald"; // TODO: Set this to "signald on <hostname>" or maybe allow client to specify
    if(request.deviceName != null) {
      deviceName = request.deviceName;
    }
    try {
      logger.info("Generating linking URI");
      URI uri = m.getDeviceLinkUri();
      this.reply("linking_uri", new JsonLinkingURI(uri), request.id);
      m.finishDeviceLink(deviceName);
      this.reply("linking_successful", new JsonAccount(m), request.id);
    } catch(TimeoutException e) {
      this.reply("linking_error", new JsonStatusMessage(1, "Timed out while waiting for device to link", request), request.id);
    } catch(UserAlreadyExists e) {
      this.reply("linking_error", new JsonStatusMessage(3, "The user " + e.getUsername() + " already exists. Delete \"" + e.getFileName() + "\" and trying again.", request), request.id);
    }
  }

  private void getUser(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    Optional<ContactTokenDetails> contact = m.getUser(request.recipientNumber);
    if(contact.isPresent()) {
      this.reply("user", new JsonContactTokenDetails(contact.get()), request.id);
    } else {
      this.reply("user_not_registered", null, request.id);
    }
  }

  private void getIdentities(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    this.reply("identities", new JsonIdentityList(request.recipientNumber, m), request.id);
  }

  private void trust(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    TrustLevel trustLevel = TrustLevel.TRUSTED_VERIFIED;
    if(request.fingerprint == null) {
      this.reply("input_error", new JsonStatusMessage(0, "Fingerprint must be a string!", request), request.id);
      return;
    }
    if(request.trustLevel != null) {
      try {
        trustLevel = TrustLevel.valueOf(request.trustLevel.toUpperCase());
      } catch(IllegalArgumentException e) {
        this.reply("input_error",
                new JsonStatusMessage(0, "Invalid TrustLevel", request), request.id);
        return;
      }
    }
    String fingerprint = request.fingerprint.replaceAll(" ", "");
    if (fingerprint.length() == 66) {
      byte[] fingerprintBytes;
      fingerprintBytes = Hex.toByteArray(fingerprint.toLowerCase(Locale.ROOT));
      boolean res = m.trustIdentity(new SignalServiceAddress(null, request.recipientNumber), fingerprintBytes, trustLevel);
      if (!res) {
        this.reply("trust_failed", new JsonStatusMessage(0, "Failed to set the trust for the fingerprint of this number, make sure the number and the fingerprint are correct.", request), request.id);
      } else {
        this.reply("trusted_fingerprint", new JsonStatusMessage(0, "Successfully trusted fingerprint", request), request.id);
      }
    } else if (fingerprint.length() == 60) {
      boolean res = m.trustIdentitySafetyNumber(new SignalServiceAddress(null, request.recipientNumber), fingerprint, trustLevel);
      if (!res) {
        this.reply("trust_failed", new JsonStatusMessage(0, "Failed to set the trust for the safety number of this number, make sure the number and the safety number are correct.", request), request.id);
      } else {
        this.reply("trusted_safety_number", new JsonStatusMessage(0, "Successfully trusted safety number", request), request.id);
      }
    } else {
      System.err.println("Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number");
      this.reply("trust_failed", new JsonStatusMessage(0, "Fingerprint has invalid format, either specify the old hex fingerprint or the new safety number", request), request.id);
    }
  }

  private void syncContacts(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    m.requestSyncContacts();
    this.reply("sync_requested", null, request.id);
  }

  private void listContacts(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    this.reply("contact_list", m.getContacts(), request.id);
  }

  public void updateContact(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager m = Manager.get(request.username);
    if(request.contact == null) {
      this.reply("update_contact_error", new JsonStatusMessage(0, "No contact specificed!", request), request.id);
      return;
    }

    if(request.contact.number == null) {
      this.reply("update_contact_error", new JsonStatusMessage(0, "No number specified! Contact must have a number", request), request.id);
      return;
    }

    m.updateContact(request.contact);
    this.reply("contact_updated", null, request.id);
  }

  private void subscribe(JsonRequest request) throws IOException, NoSuchAccountException {
    Manager.get(request.username); // throws an exception if the user doesn't exist
    if(!this.receivers.containsKey(request.username)) {
      MessageReceiver receiver = new MessageReceiver(request.username);
      this.receivers.put(request.username, receiver);
      Thread messageReceiverThread = new Thread(receiver);
      messageReceiverThread.start();
    } else {
      logger.debug("Additional subscribe request, re-using existing MessageReceiver");
    }
    this.receivers.get(request.username).subscribe(this.socket);
    this.subscribedAccounts.add(request.username);
    this.reply("subscribed", null, request.id);  // TODO: Indicate if we actually subscribed or were already subscribed, also which username it was for
  }

  private void unsubscribe(JsonRequest request) throws IOException {
    this.receivers.get(request.username).unsubscribe(this.socket);
    this.receivers.remove(request.username);
    this.subscribedAccounts.remove(request.username);
    this.reply("unsubscribed", null, request.id);  // TODO: Indicate if we actually unsubscribed or were already unsubscribed, also which username it was for
  }

  private void version() throws IOException {
      this.reply("version", new JsonVersionMessage(), null);
  }

  private void getProfile(JsonRequest request) throws IOException, InvalidCiphertextException, NoSuchAccountException, VerificationFailedException, InvalidInputException {
      Manager m = Manager.get(request.username);
      ContactInfo contact = m.getContact(request.recipientNumber);
      if(contact == null || contact.profileKey == null) {
          this.reply("profile_not_available", null, request.id);
          return;
      }
      this.reply("profile", new JsonProfile(m.getProfile(request.recipientNumber), Base64.decode(contact.profileKey)), request.id);
  }

  private void setProfile(JsonRequest request) throws IOException, NoSuchAccountException, InvalidInputException {
      Manager m = Manager.get(request.username);
      m.setProfileName(request.name);
      this.reply("profile_set", null, request.id);
  }

  private void handleError(Throwable error, JsonRequest request) {
    logger.catching(error);
    String requestid = "";
    if(request != null) {
        requestid = request.id;
    }
    try {
        this.reply("unexpected_error", new JsonStatusMessage(0, error.getMessage(), request), requestid);
    } catch(JsonProcessingException e) {
        logger.catching(error);
    }
  }
}
