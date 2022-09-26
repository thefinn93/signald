/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.google.protobuf.InvalidProtocolBufferException;
import io.finn.signald.*;
import io.finn.signald.Account;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.db.Database;
import io.finn.signald.db.IGroupsTable;
import io.finn.signald.db.IRecipientsTable;
import io.finn.signald.db.Recipient;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.exceptions.UnknownGroupException;
import io.finn.signald.util.GroupsUtil;
import io.prometheus.client.Histogram;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.VerificationFailedException;
import org.signal.libsignal.zkgroup.groups.GroupIdentifier;
import org.signal.storageservice.protos.groups.GroupChange;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.groupsv2.InvalidGroupStateException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.*;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.util.Base64;

/* Common is a collection of wrapper functions that call common functions
 * and convert their exceptions to documented v1 exceptions
 */
public class Common {
  private static final Logger logger = LogManager.getLogger();
  private static final Histogram messageSendTime =
      Histogram.build().name(BuildConfig.NAME + "_message_send_time").help("Time to send messages in seconds").labelNames("account_uuid").register();

  static ACI getACIFromIdentifier(String identifier) throws NoSuchAccountError, SQLError {
    if (identifier.startsWith("+")) {
      ACI aci;
      try {
        aci = Database.Get().AccountsTable.getACI(identifier);
      } catch (io.finn.signald.exceptions.NoSuchAccountException e) {
        throw new NoSuchAccountError(e);
      } catch (SQLException e) {
        throw new SQLError(e);
      }
      return aci;
    } else {
      return ACI.from(UUID.fromString(identifier));
    }
  }

  static Manager getManager(String identifier) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError, SQLError, NetworkError {
    return getManager(identifier, false);
  }

  static Manager getManager(String identifier, boolean offline)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError, SQLError, NetworkError {
    return getManager(getACIFromIdentifier(identifier), offline);
  }

  public static Manager getManager(ACI aci) throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError, NetworkError {
    return getManager(aci, false);
  }

  public static Manager getManager(ACI aci, boolean offline)
      throws NoSuchAccountError, ServerNotFoundError, InvalidProxyError, InternalError, AuthorizationFailedError, NetworkError {
    Manager m;
    try {
      m = Manager.get(aci, offline);
    } catch (io.finn.signald.exceptions.NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (io.finn.signald.exceptions.InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (io.finn.signald.exceptions.ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (UnknownHostException e) {
      throw new NetworkError(e);
    } catch (IOException | SQLException | InvalidKeyException e) {
      throw new InternalError("error getting manager", e);
    }
    return m;
  }

  static Recipient getRecipient(ACI aci, SignalServiceAddress address) throws InternalError { return getRecipient(Database.Get(aci).RecipientsTable, address); }
  static Recipient getRecipient(ACI aci, JsonAddress address) throws InternalError, UnregisteredUserError, AuthorizationFailedError {
    return getRecipient(Database.Get(aci).RecipientsTable, address);
  }
  public static Recipient getRecipient(ACI aci, String address) throws InternalError { return getRecipient(Database.Get(aci).RecipientsTable, address); }

  static Recipient getRecipient(IRecipientsTable table, SignalServiceAddress address) throws InternalError {
    try {
      return table.get(address);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  static Recipient getRecipient(IRecipientsTable table, JsonAddress address) throws InternalError, UnregisteredUserError, AuthorizationFailedError {
    try {
      return table.get(address);
    } catch (UnregisteredUserException e) {
      throw new UnregisteredUserError(e);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  static Recipient getRecipient(IRecipientsTable table, String address) throws InternalError {
    try {
      return table.get(address);
    } catch (SQLException | IOException e) {
      throw new InternalError("error looking up recipient", e);
    }
  }

  public static List<SendMessageResult> send(Account account, SignalServiceDataMessage.Builder messageBuilder, Recipient recipient, String recipientGroupId,
                                             List<JsonAddress> members) throws InvalidRecipientError, UnknownGroupError, InternalError, RateLimitError, InvalidRequestError,
                                                                               NoSuchAccountError, ServerNotFoundError, InvalidProxyError, AuthorizationFailedError,
                                                                               ProofRequiredError, SignalServerError {
    GroupIdentifier groupIdentifier = null;
    List<Recipient> memberRecipients = null;
    if (recipientGroupId != null) {
      try {
        groupIdentifier = new GroupIdentifier(Base64.decode(recipientGroupId));
      } catch (InvalidInputException | IOException e) {
        throw new InvalidRequestError(e.getMessage());
      }
      if (members != null) {
        memberRecipients = new ArrayList<>();
        var recipientsTable = Database.Get(account.getACI()).RecipientsTable;
        for (JsonAddress member : members) {
          try {
            memberRecipients.add(getRecipient(recipientsTable, member));
          } catch (UnregisteredUserError e) {
            logger.debug("ignoring unknown user in group while sending");
          }
        }
      }
    }

    Histogram.Timer timer = messageSendTime.labels(account.getACI().toString()).startTimer();
    try {
      MessageSender messageSender = new MessageSender(account);
      if (recipientGroupId != null) {
        return messageSender.sendGroupMessage(messageBuilder, groupIdentifier, memberRecipients);
      } else {
        return messageSender.send(messageBuilder, recipient);
      }
    } catch (io.finn.signald.exceptions.UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (RateLimitException e) {
      throw new RateLimitError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (ProofRequiredException e) {
      throw new ProofRequiredError(e);
    } catch (NonSuccessfulResponseCodeException e) {
      if (e.is5xx()) {
        throw new SignalServerError(e);
      } else {
        throw new InternalError(String.format("unexpected %s status code from server", e.getCode()), e);
      }
    } catch (IOException | SQLException | InvalidInputException | InvalidRegistrationIdException | InvalidCertificateException | InvalidKeyException | TimeoutException |
             ExecutionException | InterruptedException e) {
      throw new InternalError("error sending message", e);
    } finally {
      timer.observeDuration();
    }
  }

  public static List<SendMessageResult> sendGroupUpdateMessage(Account account, SignalServiceDataMessage.Builder message, IGroupsTable.IGroup group)
      throws SQLError, InternalError, NoSuchAccountError, UnknownGroupError, ServerNotFoundError, InvalidProxyError {
    MessageSender messageSender;
    try {
      messageSender = new MessageSender(account);
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (IOException e) {
      throw new InternalError("error getting message sender", e);
    }
    try {
      List<Recipient> allTargets = group.getMembers();
      List<Recipient> pendingMembers = group.getPendingMembers();
      if (pendingMembers != null) {
        allTargets.addAll(pendingMembers);
      }
      List<Recipient> requestingMembers = group.getRequestingMembers();
      if (requestingMembers != null) {
        allTargets.addAll(requestingMembers);
      }
      return messageSender.sendGroupMessage(message, group.getId(), allTargets);
    } catch (SQLException e) {
      throw new SQLError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (UnknownGroupException e) {
      throw new UnknownGroupError();
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (InvalidInputException | TimeoutException | InterruptedException | InvalidCertificateException | ExecutionException | InvalidKeyException |
             InvalidRegistrationIdException | IOException e) {
      throw new InternalError("error notifying new members of group", e);
    }
  }

  public static io.finn.signald.Account getAccount(String identifier) throws InternalError, NoSuchAccountError, SQLError {
    return getAccount(getACIFromIdentifier(identifier).uuid());
  }

  public static io.finn.signald.Account getAccount(UUID accountUUID) { return new Account(ACI.from(accountUUID)); }

  public static SignalDependencies getDependencies(UUID accountUUID) throws InvalidProxyError, ServerNotFoundError, InternalError, NoSuchAccountError {
    try {
      return SignalDependencies.get(accountUUID);
    } catch (SQLException | IOException e) {
      throw new InternalError("error reading local account state", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }
  }

  public static IGroupsTable.IGroup getGroup(ACI aci, String groupId) throws UnknownGroupError, InvalidRequestError, InternalError {
    GroupIdentifier groupIdentifier;
    try {
      groupIdentifier = new GroupIdentifier(Base64.decode(groupId));
    } catch (InvalidInputException | IOException e) {
      throw new InvalidRequestError(e.getMessage());
    }

    Optional<IGroupsTable.IGroup> g;
    try {
      g = Database.Get(aci).GroupsTable.get(groupIdentifier);
    } catch (SQLException | InvalidProtocolBufferException | InvalidInputException e) {
      throw new InternalError("error getting group", e);
    }
    if (g.isEmpty()) {
      throw new UnknownGroupError();
    }
    return g.get();
  }

  public static IGroupsTable.IGroup getGroup(Account account, String groupId)
      throws UnknownGroupError, NoSuchAccountError, ServerNotFoundError, InvalidRequestError, InternalError, InvalidProxyError, AuthorizationFailedError {
    return getGroup(account, groupId, -1);
  }

  public static IGroupsTable.IGroup getGroup(Account account, String groupId, int revision)
      throws UnknownGroupError, InvalidRequestError, InternalError, NoSuchAccountError, InvalidProxyError, ServerNotFoundError, AuthorizationFailedError {
    Groups groups = getGroups(account);

    GroupIdentifier groupIdentifier;
    try {
      groupIdentifier = new GroupIdentifier(Base64.decode(groupId));
    } catch (InvalidInputException | IOException e) {
      throw new InvalidRequestError(e.getMessage());
    }

    Optional<IGroupsTable.IGroup> g;
    try {
      g = Database.Get(account.getACI()).GroupsTable.get(groupIdentifier);
    } catch (SQLException | InvalidProtocolBufferException | InvalidInputException e) {
      throw new InternalError("error getting group", e);
    }
    if (g.isEmpty()) {
      throw new UnknownGroupError();
    }

    Optional<IGroupsTable.IGroup> groupOptional;
    try {
      groupOptional = groups.getGroup(g.get().getMasterKey(), revision);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (InvalidGroupStateException | VerificationFailedException | IOException | InvalidInputException | SQLException e) {
      throw new InternalError("error fetching group state", e);
    }
    if (groupOptional.isEmpty()) {
      throw new UnknownGroupError();
    }

    return groupOptional.get();
  }

  public static GroupsV2Operations.GroupOperations getGroupOperations(Account account, IGroupsTable.IGroup group) throws InternalError, ServerNotFoundError, InvalidProxyError {
    try {
      GroupsV2Operations operations = GroupsUtil.GetGroupsV2Operations(account.getServiceConfiguration());
      return operations.forGroup(group.getSecretParams());
    } catch (SQLException | IOException e) {
      throw new InternalError("error getting service configuration", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }
  }

  public static List<SendMessageResult> updateGroup(Account account, IGroupsTable.IGroup group, GroupChange.Actions.Builder change)
      throws InternalError, InvalidProxyError, NoSuchAccountError, ServerNotFoundError, AuthorizationFailedError, GroupPatchNotAcceptedError, UnknownGroupError, SQLError {
    Pair<SignalServiceDataMessage.Builder, IGroupsTable.IGroup> updateOutput;
    try {
      updateOutput = getGroups(account).updateGroup(group, change);
    } catch (GroupPatchNotAcceptedException e) {
      throw new GroupPatchNotAcceptedError(e);
    } catch (AuthorizationFailedException e) {
      throw new AuthorizationFailedError(e);
    } catch (IOException | VerificationFailedException | SQLException | InvalidInputException e) {
      throw new InternalError("error committing group change", e);
    }

    return sendGroupUpdateMessage(account, updateOutput.first(), group);
  }

  public static Groups getGroups(Account account) throws InvalidProxyError, NoSuchAccountError, ServerNotFoundError, InternalError {
    try {
      return account.getGroups();
    } catch (SQLException | IOException e) {
      throw new InternalError("error getting group access", e);
    } catch (ServerNotFoundException e) {
      throw new ServerNotFoundError(e);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (InvalidProxyException e) {
      throw new InvalidProxyError(e);
    }
  }
}
