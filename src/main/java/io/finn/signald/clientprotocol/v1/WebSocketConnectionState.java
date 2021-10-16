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
