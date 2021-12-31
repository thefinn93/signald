/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;

@Doc("indicates when the websocket connection state to the signal server has changed")
public class WebSocketConnectionState {
  @Doc("One of: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING, AUTHENTICATION_FAILED, FAILED") public String state;
  @Doc("One of: UNIDENTIFIED, IDENTIFIED") public String socket;
  public WebSocketConnectionState(org.whispersystems.signalservice.api.websocket.WebSocketConnectionState state, boolean unidentified) {
    this.state = state.name();
    socket = unidentified ? "UNIDENTIFIED" : "IDENTIFIED";
  }
}
