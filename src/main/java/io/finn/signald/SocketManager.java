package io.finn.signald;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.net.Socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonGenerator;


class SocketManager {
  private List<Socket> sockets = Collections.synchronizedList(new ArrayList<Socket>());
  private ObjectMapper mpr = new ObjectMapper();

  public SocketManager() {
    this.mpr.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY); // disable autodetect
    this.mpr.enable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    this.mpr.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.mpr.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
  }

  public void add(Socket s) {
    this.sockets.add(s);
  }

  public void remove(Socket s) {
    this.sockets.remove(s);
  }

  public void broadcast(JsonMessageWrapper message) throws JsonProcessingException, IOException {
    synchronized(this.sockets) {
      Iterator i = this.sockets.iterator();
      while(i.hasNext()) {
        send(message, (Socket)i.next());
      }
    }
  }

  public void send(JsonMessageWrapper message, Socket s) throws JsonProcessingException, IOException {
    String jsonmessage = this.mpr.writeValueAsString(message);
    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
    out.println(jsonmessage);
  }
}
