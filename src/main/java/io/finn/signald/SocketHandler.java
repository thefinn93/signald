/**
 * Copyright (C) 2018 Finn Herzfeld
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

import org.whispersystems.signalservice.api.push.exceptions.EncapsulatedExceptions;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;

import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.UserAlreadyExists;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SocketHandler implements Runnable {
  private BufferedReader reader;
  private PrintWriter writer;
  private ConcurrentHashMap<String,Manager> managers;
  private ConcurrentHashMap<String,MessageReceiver> receivers;
  private ObjectMapper mpr = new ObjectMapper();
  private static final Logger logger = LogManager.getLogger();
  private Socket socket;
  private ArrayList<String> subscribedAccounts = new ArrayList<String>();

  public SocketHandler(Socket socket, ConcurrentHashMap<String,MessageReceiver> receivers, ConcurrentHashMap<String,Manager> managers) throws IOException {
    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.writer = new PrintWriter(socket.getOutputStream(), true);
    this.socket = socket;
    this.managers = managers;
    this.receivers = receivers;

    this.mpr.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
    this.mpr.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    this.mpr.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.mpr.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  public void run() {
    logger.info("Client connected");

    try {
      this.reply("version", new JsonVersionMessage(), null);
    } catch(JsonProcessingException e) {
      handleError(e, null);
    }

    while(true) {
      String line = null;
      JsonRequest request;
      try {
        line = this.reader.readLine();
        if(line == null) {
          logger.info("Client disconnected");
          this.reader.close();
          this.writer.close();
          for(Map.Entry<String, MessageReceiver> entry : this.receivers.entrySet()) {
            if(entry.getValue().unsubscribe(this.socket)) {
              logger.info("Unsubscribed from " + entry.getKey());
            }
          }
          return;
        }
        if(!line.equals("")) {
            logger.debug(line);
            request = this.mpr.readValue(line, JsonRequest.class);
            try {
                handleRequest(request);
            } catch(Throwable e) {
                handleError(e, request);
            }
        }
      } catch(IOException e) {
        handleError(e, null);
        break;
      }
    }
  }

  private void handleRequest(JsonRequest request) throws Throwable {
    switch(request.type) {
      case "send":
        send(request);
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
      case "sync_contacts":
        syncContacts(request);
        break;
      case "list_contacts":
        listContacts(request);
        break;
      case "version":
        version();
        break;
      default:
        logger.warn("Unknown command type " + request.type);
        this.reply("unknown_command", new JsonStatusMessage(5, "Unknown command type " + request.type, request), request.id);
        break;
    }
  }

  private void send(JsonRequest request) throws IOException {
    Manager manager = getManager(request.username);
    try {
      if(request.recipientGroupId != null) {
        byte[] groupId = Base64.decode(request.recipientGroupId);
        manager.sendGroupMessage(request.messageBody, request.attachmentFilenames, groupId);
      } else {
        manager.sendMessage(request.messageBody, request.attachmentFilenames, request.recipientNumber);
      }
    } catch(EncapsulatedExceptions | AttachmentInvalidException | GroupNotFoundException | NotAGroupMemberException | IOException e) {
      logger.catching(e);
    }
  }

  private void listAccounts(JsonRequest request) throws JsonProcessingException, IOException {
    // We have to create a manager for each account that we're listing, which is all of them :/
    String settingsPath = System.getProperty("user.home") + "/.config/signal";
    File[] users = new File(settingsPath + "/data").listFiles();
    for(int i = 0; i < users.length; i++) {
      if(!users[i].isDirectory()) {
        getManager(users[i].getName());
      }
    }

    JsonAccountList accounts = new JsonAccountList(this.managers, this.subscribedAccounts);
    this.reply("account_list", accounts, request.id);
  }

  private void register(JsonRequest request) throws IOException {
    logger.info("Register request: " + request);
    Manager m = getManager(request.username);
    Boolean voice = false;
    if(request.voice != null) {
      voice = request.voice;
    }

    if(!m.userHasKeys()) {
      logger.info("User has no keys, making some");
      m.createNewIdentity();
    }
    logger.info("Registering (voice: " + voice + ")");
    m.register(voice);
    this.reply("verification_required", new JsonAccount(m), request.id);
  }

  private void verify(JsonRequest request) throws IOException {
    Manager m = getManager(request.username);
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

  private void addDevice(JsonRequest request) throws IOException, InvalidKeyException, AssertionError, URISyntaxException {
    Manager m = getManager(request.username);
    m.addDeviceLink(new URI(request.uri));
    reply("device_added", new JsonStatusMessage(4, "Successfully linked device"), request.id);
  }

  private Manager getManager(String username) throws IOException {
    // So many problems in this method, need to have a single place to create new managers, probably in MessageReceiver
    String settingsPath = System.getProperty("user.home") + "/.config/signal";  // TODO: Stop hard coding this everywhere

    if(this.managers.containsKey(username)) {
      return this.managers.get(username);
    } else {
      logger.info("Creating a manager for " + username);
      Manager m = new Manager(username, settingsPath);
      if(m.userExists()) {
        m.init();
      } else {
        logger.warn("Created manager for a user that doesn't exist! (" + username + ")");
      }
      this.managers.put(username, m);
      return m;
    }
  }

  private void updateGroup(JsonRequest request) throws IOException, EncapsulatedExceptions, GroupNotFoundException, AttachmentInvalidException, NotAGroupMemberException {
    Manager m = getManager(request.username);

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
        groupMembers = new ArrayList<String>();
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

  private void setExpiration(JsonRequest request) throws IOException, GroupNotFoundException, NotAGroupMemberException, AttachmentInvalidException, EncapsulatedExceptions, IOException {
    Manager m = getManager(request.username);

    if(request.recipientGroupId != null) {
      byte[] groupId = Base64.decode(request.recipientGroupId);
      m.setExpiration(groupId, request.expiresInSeconds);
    } else {
      m.setExpiration(request.recipientNumber, request.expiresInSeconds);
    }

    this.reply("expiration_updated", null, request.id);
  }

  private void listGroups(JsonRequest request) throws IOException, JsonProcessingException {
    Manager m = getManager(request.username);
    this.reply("group_list", new JsonGroupList(m), request.id);
  }

  private void leaveGroup(JsonRequest request) throws IOException, JsonProcessingException, GroupNotFoundException, EncapsulatedExceptions, NotAGroupMemberException {
    Manager m = getManager(request.username);
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


  private void link(JsonRequest request) throws AssertionError, IOException, InvalidKeyException {
    String settingsPath = System.getProperty("user.home") + "/.config/signal";  // TODO: Stop hard coding this everywhere
    Manager m = new Manager(null, settingsPath);
    m.createNewIdentity();
    String deviceName = "signald"; // TODO: Set this to "signald on <hostname>"
    if(request.deviceName != null) {
      deviceName = request.deviceName;
    }
    try {
      m.getDeviceLinkUri();
      this.reply("linking_uri", new JsonLinkingURI(m), request.id);
      m.finishDeviceLink(deviceName);
      this.managers.put(m.getUsername(), m);
      this.reply("linking_successful", new JsonAccount(m), request.id);
    } catch(TimeoutException e) {
      this.reply("linking_error", new JsonStatusMessage(1, "Timed out while waiting for device to link", request), request.id);
    } catch(IOException e) {
      this.reply("linking_error", new JsonStatusMessage(2, e.getMessage(), request), request.id);
    } catch(UserAlreadyExists e) {
      this.reply("linking_error", new JsonStatusMessage(3, "The user " + e.getUsername() + " already exists. Delete \"" + e.getFileName() + "\" and trying again.", request), request.id);
    }
  }

  private void getUser(JsonRequest request) throws IOException {
    Manager m = getManager(request.username);
    Optional<ContactTokenDetails> contact = m.getUser(request.recipientNumber);
    if(contact.isPresent()) {
      this.reply("user", new JsonContactTokenDetails(contact.get()), request.id);
    } else {
      this.reply("user_not_registered", null, request.id);
    }
  }

  private void getIdentities(JsonRequest request) throws IOException {
    Manager m = getManager(request.username);
    this.reply("identities", new JsonIdentityList(request.recipientNumber, m), request.id);
  }

  private void syncContacts(JsonRequest request) throws IOException {
    Manager m = getManager(request.username);
    m.requestSyncContacts();
    this.reply("sync_requested", null, request.id);
  }

  private void listContacts(JsonRequest request) throws IOException {
    Manager m = getManager(request.username);
    this.reply("contact_list", m.getContacts(), request.id);
  }

  private void subscribe(JsonRequest request) throws IOException {
    if(!this.receivers.containsKey(request.username)) {
      MessageReceiver receiver = new MessageReceiver(request.username, this.managers);
      this.receivers.put(request.username, receiver);
      Thread messageReceiverThread = new Thread(receiver);
      messageReceiverThread.start();
    }
    this.receivers.get(request.username).subscribe(this.socket);
    this.subscribedAccounts.add(request.username);
    this.reply("subscribed", null, request.id);  // TODO: Indicate if we actually subscribed or were already subscribed, also which username it was for
  }

  private void unsubscribe(JsonRequest request) throws IOException {
    this.receivers.get(request.username).unsubscribe(this.socket);
    this.subscribedAccounts.remove(request.username);
    this.reply("unsubscribed", null, request.id);  // TODO: Indicate if we actually unsubscribed or were already unsubscribed, also which username it was for
  }

  private void version() throws IOException {
      this.reply("version", new JsonVersionMessage(), null);
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
