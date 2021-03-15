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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.finn.signald.util.JSONUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class SocketManager {
  private final static ObjectMapper mapper = JSONUtil.GetMapper();
  private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());

  public void add(Socket s) {
    synchronized (this.sockets) { this.sockets.add(s); }
  }

  public boolean remove(Socket s) {
    synchronized (this.sockets) { return this.sockets.remove(s); }
  }

  public int size() {
    synchronized (this.sockets) { return this.sockets.size(); }
  }

  public void broadcast(JsonMessageWrapper message) throws IOException {
    synchronized (this.sockets) {
      Iterator i = this.sockets.iterator();
      while (i.hasNext()) {
        Socket s = (Socket)i.next();
        if (s.isClosed()) {
          this.remove(s);
        } else {
          send(message, s);
        }
      }
    }
  }

  public void send(JsonMessageWrapper message, Socket s) throws IOException {
    String JSONMessage = mapper.writeValueAsString(message);
    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
    out.println(JSONMessage);
  }
}
