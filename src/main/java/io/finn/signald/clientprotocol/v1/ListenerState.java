/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.annotations.Doc;

@Doc("prior attempt to indicate signald connectivity state. WebSocketConnectionState messages will be delivered at the "
     + " same time as well as in other parts of the websocket lifecycle.")
public class ListenerState {
  public boolean connected;
  public ListenerState(boolean c) { connected = c; }
}
