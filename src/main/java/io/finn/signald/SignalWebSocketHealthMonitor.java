/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import io.prometheus.client.Gauge;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalWebSocket;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.websocket.HealthMonitor;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

public final class SignalWebSocketHealthMonitor implements HealthMonitor {
  static final Gauge connected =
      Gauge.build().name(BuildConfig.NAME + "_upstream_websocket").help("1 if the upstream websocket is connected, 0 otherwise").labelNames("account_uuid", "socket").register();
  private final static Logger logger = LogManager.getLogger();

  private static final long KEEP_ALIVE_SEND_CADENCE = TimeUnit.SECONDS.toMillis(WebSocketConnection.KEEPALIVE_TIMEOUT_SECONDS);
  private static final long MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE = KEEP_ALIVE_SEND_CADENCE * 3;

  private SignalWebSocket signalWebSocket;
  private final SleepTimer sleepTimer;

  private volatile KeepAliveSender keepAliveSender;

  private final HealthState identified = new HealthState();
  private final HealthState unidentified = new HealthState();

  private final UUID accountUUID;

  public SignalWebSocketHealthMonitor(UUID accountUUID, SleepTimer sleepTimer) {
    this.sleepTimer = sleepTimer;
    this.accountUUID = accountUUID;
  }

  public void monitor(SignalWebSocket signalWebSocket) {
    Preconditions.checkNotNull(signalWebSocket);
    Preconditions.checkArgument(this.signalWebSocket == null, "monitor can only be called once");

    this.signalWebSocket = signalWebSocket;

    // noinspection ResultOfMethodCallIgnored
    signalWebSocket.getWebSocketState()
        .subscribeOn(Schedulers.computation())
        .observeOn(Schedulers.computation())
        .distinctUntilChanged()
        .subscribe(s -> onStateChange(s, identified, false));

    // noinspection ResultOfMethodCallIgnored
    signalWebSocket.getUnidentifiedWebSocketState()
        .subscribeOn(Schedulers.computation())
        .observeOn(Schedulers.computation())
        .distinctUntilChanged()
        .subscribe(s -> onStateChange(s, unidentified, true));
  }

  private synchronized void onStateChange(WebSocketConnectionState state, HealthState healthState, boolean unidentified) throws SQLException {
    logger.debug((unidentified ? "unidentified" : "identified") + " websocket state: " + state.name());

    connected.labels(accountUUID.toString(), unidentified ? "unidentified" : "identified").set(state == WebSocketConnectionState.CONNECTED ? 1 : 0);

    MessageReceiver.handleWebSocketConnectionStateChange(accountUUID, state, unidentified);

    healthState.needsKeepAlive = state == WebSocketConnectionState.CONNECTED;

    if (keepAliveSender == null && isKeepAliveNecessary()) {
      keepAliveSender = new KeepAliveSender();
      keepAliveSender.start();
    } else if (keepAliveSender != null && !isKeepAliveNecessary()) {
      keepAliveSender.shutdown();
      keepAliveSender = null;
    }
  }

  @Override
  public void onKeepAliveResponse(long sentTimestamp, boolean isIdentifiedWebSocket) {
    if (isIdentifiedWebSocket) {
      identified.lastKeepAliveReceived = System.currentTimeMillis();
    } else {
      unidentified.lastKeepAliveReceived = System.currentTimeMillis();
    }
  }

  @Override
  public void onMessageError(int status, boolean isIdentifiedWebSocket) {
    if (status == 409) {
      HealthState healthState = (isIdentifiedWebSocket ? identified : unidentified);
      if (healthState.mismatchErrorTracker.addSample(System.currentTimeMillis())) {
        logger.warn("Received too many mismatch device errors, forcing new websockets.");
        signalWebSocket.forceNewWebSockets();
      }
    }
  }

  private boolean isKeepAliveNecessary() { return identified.needsKeepAlive || unidentified.needsKeepAlive; }

  private static class HealthState {

    private final HttpErrorTracker mismatchErrorTracker = new HttpErrorTracker(5, TimeUnit.MINUTES.toMillis(1));

    private volatile boolean needsKeepAlive;
    private volatile long lastKeepAliveReceived;
  }

  /**
   * Sends periodic heartbeats/keep-alives over both WebSockets to prevent connection timeouts. If
   * either WebSocket fails 3 times to get a return heartbeat both are forced to be recreated.
   */
  private class KeepAliveSender extends Thread {

    private volatile boolean shouldKeepRunning = true;

    public void run() {
      identified.lastKeepAliveReceived = System.currentTimeMillis();
      unidentified.lastKeepAliveReceived = System.currentTimeMillis();

      while (shouldKeepRunning && isKeepAliveNecessary()) {
        try {
          sleepTimer.sleep(KEEP_ALIVE_SEND_CADENCE);

          if (shouldKeepRunning && isKeepAliveNecessary()) {
            long keepAliveRequiredSinceTime = System.currentTimeMillis() - MAX_TIME_SINCE_SUCCESSFUL_KEEP_ALIVE;

            if (identified.lastKeepAliveReceived < keepAliveRequiredSinceTime || unidentified.lastKeepAliveReceived < keepAliveRequiredSinceTime) {
              logger.warn("Missed keep alives, identified last: " + identified.lastKeepAliveReceived + " unidentified last: " + unidentified.lastKeepAliveReceived +
                          " needed by: " + keepAliveRequiredSinceTime);
              signalWebSocket.forceNewWebSockets();
            } else {
              signalWebSocket.sendKeepAlive();
            }
          }
        } catch (Throwable e) {
          logger.warn("Error occurred in KeepAliveSender, ignoring ...", e);
        }
      }
    }

    public void shutdown() { shouldKeepRunning = false; }
  }

  private final static class HttpErrorTracker {

    private final long[] timestamps;
    private final long errorTimeRange;

    public HttpErrorTracker(int samples, long errorTimeRange) {
      this.timestamps = new long[samples];
      this.errorTimeRange = errorTimeRange;
    }

    public synchronized boolean addSample(long now) {
      long errorsMustBeAfter = now - errorTimeRange;
      int count = 1;
      int minIndex = 0;

      for (int i = 0; i < timestamps.length; i++) {
        if (timestamps[i] < errorsMustBeAfter) {
          timestamps[i] = 0;
        } else if (timestamps[i] != 0) {
          count++;
        }

        if (timestamps[i] < timestamps[minIndex]) {
          minIndex = i;
        }
      }

      timestamps[minIndex] = now;

      if (count >= timestamps.length) {
        Arrays.fill(timestamps, 0);
        return true;
      }
      return false;
    }
  }
}
