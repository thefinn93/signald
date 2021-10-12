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

import io.finn.signald.db.AccountsTable;
import io.finn.signald.db.DatabaseProtocolStore;
import io.finn.signald.db.ServersTable;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.*;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.WebSocketFactory;
import org.whispersystems.signalservice.internal.util.DynamicCredentialsProvider;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

public class SignalDependencies {
  private final static Map<String, SignalDependencies> instances = new HashMap<>();

  private final ServersTable.Server server;
  private final DatabaseProtocolStore dataStore;
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

  public static SignalDependencies get(UUID accountUUID) throws SQLException, ServerNotFoundException, InvalidProxyException, IOException, NoSuchAccountException {
    synchronized (instances) {
      SignalDependencies d = instances.get(accountUUID.toString());
      if (d == null) {
        ServersTable.Server server = AccountsTable.getServer(accountUUID);
        Account account = new Account(accountUUID);
        d = new SignalDependencies(account, server);
        instances.put(accountUUID.toString(), d);
      }
      return d;
    }
  }

  public static void delete(UUID accountUUID) {
    synchronized (instances) {
      SignalDependencies dependencies = instances.remove(accountUUID.toString());
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

  private SignalDependencies(Account account, ServersTable.Server server) throws SQLException, NoSuchAccountException {
    dataStore = account.getProtocolStore();
    credentialsProvider = account.getCredentialsProvider();
    this.server = server;
    sessionLock = new SessionLock(account);
  }

  public SignalWebSocket getWebSocket() {
    synchronized (websocketLock) {
      if (websocket == null) {
        UptimeSleepTimer timer = new UptimeSleepTimer();
        SignalWebSocketHealthMonitor healthMonitor = new SignalWebSocketHealthMonitor(timer);
        WebSocketFactory webSocketFactory = new WebSocketFactory() {
          @Override
          public WebSocketConnection createWebSocket() {
            return new WebSocketConnection("normal", server.getSignalServiceConfiguration(), Optional.of(credentialsProvider), BuildConfig.USER_AGENT, healthMonitor);
          }

          @Override
          public WebSocketConnection createUnidentifiedWebSocket() {
            return new WebSocketConnection("unidentified", server.getSignalServiceConfiguration(), Optional.absent(), BuildConfig.USER_AGENT, healthMonitor);
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
        ClientZkProfileOperations profileOperations = ClientZkOperations.create(server.getSignalServiceConfiguration()).getProfileOperations();
        messageReceiver = new SignalServiceMessageReceiver(server.getSignalServiceConfiguration(), credentialsProvider, BuildConfig.USER_AGENT, profileOperations,
                                                           ServiceConfig.AUTOMATIC_NETWORK_RETRY);
      }
    }
    return messageReceiver;
  }

  public SignalServiceMessageSender getMessageSender() {
    synchronized (messageSenderLock) {
      if (messageSender == null) {
        ClientZkProfileOperations profileOperations = ClientZkOperations.create(server.getSignalServiceConfiguration()).getProfileOperations();
        messageSender = new SignalServiceMessageSender(server.getSignalServiceConfiguration(), credentialsProvider, dataStore, sessionLock, BuildConfig.USER_AGENT, getWebSocket(),
                                                       Optional.absent(), profileOperations, executor, ServiceConfig.MAX_ENVELOPE_SIZE, ServiceConfig.AUTOMATIC_NETWORK_RETRY);
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
}
