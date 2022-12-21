/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.finn.signald.db.Database;
import io.finn.signald.db.DatabaseDataStore;
import io.finn.signald.db.IServersTable;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import io.finn.signald.util.GroupsUtil;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.whispersystems.signalservice.api.*;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.services.ProfileService;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

public class SignalDependencies {
  private final static Map<String, SignalDependencies> instances = new HashMap<>();

  private final IServersTable.AbstractServer server;
  private final DatabaseDataStore dataStore;
  private final DynamicCredentialsProvider credentialsProvider;

  private final SessionLock sessionLock;
  private final ExecutorService executor = Executors.newCachedThreadPool();

  private SignalWebSocket websocket;
  private final Object websocketLock = new Object();

  private SignalServiceMessageReceiver messageReceiver;
  private final Object messageReceiverLock = new Object();

  private SignalServiceMessageSender messageSender;
  private final Object messageSenderLock = new Object();

  private KeyBackupService keyBackupService;
  private final Object keyBackupServiceLock = new Object();

  private SignalServiceAccountManager accountManager;
  private final Object accountManagerLock = new Object();

  private ProfileService profileService;
  private final Object profileServiceLock = new Object();

  private final UUID accountUUID;

  public static SignalDependencies get(UUID accountUUID) throws SQLException, ServerNotFoundException, InvalidProxyException, IOException, NoSuchAccountException {
    return get(ACI.from(accountUUID));
  }

  public static SignalDependencies get(ACI aci) throws SQLException, ServerNotFoundException, InvalidProxyException, IOException, NoSuchAccountException {
    synchronized (instances) {
      SignalDependencies d = instances.get(aci.toString());
      if (d == null) {
        var server = Database.Get().AccountsTable.getServer(aci);
        Account account = new Account(aci);
        d = new SignalDependencies(account, server);
        instances.put(aci.toString(), d);
      }
      return d;
    }
  }

  public static void delete(ACI aci) {
    synchronized (instances) {
      SignalDependencies dependencies = instances.remove(aci.toString());
      if (dependencies == null) {
        return;
      }

      dependencies.executor.shutdown();

      synchronized (dependencies.websocketLock) {
        if (dependencies.websocket != null) {
          dependencies.websocket.disconnect();
        }
      }
    }
  }

  private SignalDependencies(Account account, IServersTable.AbstractServer server) throws SQLException, NoSuchAccountException {
    dataStore = account.getDataStore();
    credentialsProvider = account.getCredentialsProvider();
    this.server = server;
    accountUUID = account.getUUID();
    sessionLock = new SessionLock(account);
  }

  public SignalWebSocket getWebSocket() {
    synchronized (websocketLock) {
      if (websocket == null) {
        UptimeSleepTimer timer = new UptimeSleepTimer();
        SignalWebSocketHealthMonitor healthMonitor = new SignalWebSocketHealthMonitor(accountUUID, timer);
        WebSocketFactory webSocketFactory = new WebSocketFactory() {
          @Override
          public WebSocketConnection createWebSocket() {
            return new WebSocketConnection("normal", server.getSignalServiceConfiguration(), Optional.of(credentialsProvider), BuildConfig.USER_AGENT, healthMonitor, true);
          }

          @Override
          public WebSocketConnection createUnidentifiedWebSocket() {
            return new WebSocketConnection("unidentified", server.getSignalServiceConfiguration(), Optional.empty(), BuildConfig.USER_AGENT, healthMonitor, true);
          }
        };
        websocket = new SignalWebSocket(webSocketFactory);
        healthMonitor.monitor(websocket);
      }
    }
    return websocket;
  }

  public SignalServiceMessageReceiver getMessageReceiver() {
    synchronized (messageReceiverLock) {
      if (messageReceiver == null) {
        ClientZkProfileOperations profileOperations = getProfileOperations();
        messageReceiver = new SignalServiceMessageReceiver(server.getSignalServiceConfiguration(), credentialsProvider, BuildConfig.USER_AGENT, profileOperations,
                                                           ServiceConfig.AUTOMATIC_NETWORK_RETRY);
      }
    }
    return messageReceiver;
  }

  public SignalServiceMessageSender getMessageSender() {
    synchronized (messageSenderLock) {
      if (messageSender == null) {
        ClientZkProfileOperations profileOperations = getProfileOperations();
        messageSender = new SignalServiceMessageSender(server.getSignalServiceConfiguration(), credentialsProvider, dataStore, sessionLock, BuildConfig.USER_AGENT, getWebSocket(),
                                                       Optional.empty(), profileOperations, executor, ServiceConfig.MAX_ENVELOPE_SIZE, ServiceConfig.AUTOMATIC_NETWORK_RETRY);
      }
    }
    return messageSender;
  }

  public KeyBackupService getKeyBackupService() throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
    synchronized (keyBackupServiceLock) {
      if (keyBackupService == null) {
        keyBackupService =
            accountManager.getKeyBackupService(server.getIASKeyStore(), server.getKeyBackupServiceName(), server.getKeyBackupServiceId(), server.getKeyBackupMrenclave(), 10);
      }
    }
    return keyBackupService;
  }

  public SignalServiceAccountManager getAccountManager() {
    synchronized (accountManagerLock) {
      if (accountManager == null) {
        accountManager = new SignalServiceAccountManager(server.getSignalServiceConfiguration(), credentialsProvider, BuildConfig.SIGNAL_AGENT,
                                                         GroupsUtil.GetGroupsV2Operations(server.getSignalServiceConfiguration()), true);
      }
    }
    return accountManager;
  }

  public SessionLock getSessionLock() { return sessionLock; }

  public ProfileService getProfileService() {
    synchronized (profileServiceLock) {
      if (profileService == null) {
        ClientZkProfileOperations profileOperations = getProfileOperations();
        profileService = new ProfileService(profileOperations, getMessageReceiver(), getWebSocket());
      }
    }
    return profileService;
  }

  private ClientZkProfileOperations getProfileOperations() { return ClientZkOperations.create(server.getSignalServiceConfiguration()).getProfileOperations(); }
}
