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

import org.asamk.signal.AttachmentInvalidException;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonGenerator;

public class SocketHandler implements Runnable {
  private BufferedReader reader;
  private ConcurrentHashMap<String,Manager> managers;
  private ObjectMapper mpr = new ObjectMapper();


  public SocketHandler(Socket socket, ConcurrentHashMap<String,Manager> managers) throws IOException {
    this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    this.managers = managers;

    this.mpr.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
    this.mpr.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    this.mpr.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.mpr.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  public void run() {
    while(true) {
      String line = null;
      JsonRequest request;
      try {
        line = this.reader.readLine();
      } catch(IOException e) {
        System.err.println("Error parsing input:");
        e.printStackTrace();
        break;
      }
      if(line != null && !line.equals("")) {
        try {
          System.out.println(line);
          request = this.mpr.readValue(line, JsonRequest.class);
          handleRequest(request);
        } catch(Exception e) {
          e.printStackTrace();
          System.err.println("+-- Original request text: " + line);
        }
      }
    }
  }

  private void handleRequest(JsonRequest request) {
    System.out.println("Pretending to handle request...");
    switch(request.type) {
      case "send":
        send(request);
        break;
      default:
        System.err.println("Unknown command type " + request.type);
        break;
    }
  }

  private void send(JsonRequest request) {
    Manager manager = this.managers.get(request.sourceNumber);
    try {
      manager.sendMessage(request.messageBody, request.attachmentFilenames, request.recipientNumber);
    } catch(EncapsulatedExceptions | AttachmentInvalidException | IOException e) {
      e.printStackTrace();
    }
  }
}
