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

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import org.asamk.signal.AttachmentInvalidException;
import org.asamk.signal.GroupNotFoundException;
import org.asamk.signal.NotAGroupMemberException;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

public class Main {
  static String SOCKET_PATH = "signald.sock";

  public static void main(String[] args) {
    try {

      // Workaround for BKS truststore
      Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);

      ConcurrentHashMap<String,Manager> managers = new ConcurrentHashMap<String,Manager>();
      SocketManager socketmanager = new SocketManager();

      // Spins up one thread per registered signal number, listens for incoming messages
      String settingsPath = System.getProperty("user.home") + "/.config/signal";

      File[] users = new File(settingsPath + "/data").listFiles();
      for(int i = 0; i < users.length; i++) {
        if(!users[i].isDirectory()) {
          String username = users[i].getName();
          System.out.println("Creating new manager for " + username);
          Manager m = new Manager(users[i].getName(), settingsPath);
          if (m.userExists()) {
            try {
              m.init();
              managers.put(username, m);
              Thread messageReceiverThread = new Thread(new MessageReceiver(m, socketmanager));
              messageReceiverThread.start();
            } catch (org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException e) {
              System.err.println("Authorization Failed for " + username);
            } catch (Exception e) {
              System.err.println("Error loading state file " + m.getFileName());
              e.printStackTrace();
              System.exit(2);
            }
          }
        }
      }

      // Spins up one thread per inbound connection to the control socket
      AFUNIXServerSocket server = AFUNIXServerSocket.newInstance();
      server.bind(new AFUNIXSocketAddress(new File(SOCKET_PATH)));

      while (!Thread.interrupted()) {
        System.out.println("Waiting for connection...");
        try {
          Socket socket = server.accept();
          socketmanager.add(socket);

          System.out.println("Connected: " + socket);

          // Kick off the thread to read input
          Thread socketHandlerThread = new Thread(new SocketHandler(socket, managers));
          socketHandlerThread.start();

        } catch(IOException e) {
          e.printStackTrace();
        }
      }

    } catch(Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
