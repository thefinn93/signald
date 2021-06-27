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

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.JsonCallMessage;
import io.finn.signald.JsonReceiptMessage;
import io.finn.signald.JsonTypingMessage;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.exceptions.NoSuchAccountException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@Deprecated(1641027661)
public class JsonMessageEnvelope {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) public String username;
  @ExampleValue(ExampleValue.LOCAL_UUID) public String uuid;
  public JsonAddress source;
  public int sourceDevice;
  public String type;
  public String relay;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;
  public String timestampISO;
  public long serverTimestamp; // newer versions of signal call this serverReceivedTimestamp
  @ExampleValue(ExampleValue.MESSAGE_ID + 80) public long serverDeliveredTimestamp;
  public boolean hasLegacyMessage;
  public boolean hasContent;
  public boolean isUnidentifiedSender;
  public JsonDataMessage dataMessage;
  public JsonSyncMessage syncMessage;
  public JsonCallMessage callMessage;
  public JsonReceiptMessage receipt;
  public JsonTypingMessage typing;

  public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent c, String username) throws IOException, SQLException, NoSuchAccountException {
    this.username = username;

    if (envelope.hasServerGuid()) {
      uuid = envelope.getServerGuid();
    }

    Manager m = Manager.get(username);
    if (envelope.hasSource()) {
      source = new JsonAddress(m.getResolver().resolve(envelope.getSourceAddress()));
    } else if (c != null) {
      source = new JsonAddress(m.getResolver().resolve(c.getSender()));
    }

    if (envelope.hasSourceDevice()) {
      sourceDevice = envelope.getSourceDevice();
    }

    if (source != null) {
      if (source.getSignalServiceAddress().getRelay().isPresent()) {
        relay = source.getSignalServiceAddress().getRelay().get();
      }
    }

    type = SignalServiceProtos.Envelope.Type.forNumber(envelope.getType()).toString();
    timestamp = envelope.getTimestamp();
    timestampISO = formatTimestampISO(envelope.getTimestamp());
    serverTimestamp = envelope.getServerReceivedTimestamp();
    serverDeliveredTimestamp = envelope.getServerDeliveredTimestamp();
    hasLegacyMessage = envelope.hasLegacyMessage();
    hasContent = envelope.hasContent();

    if (c != null) {
      if (c.getDataMessage().isPresent()) {
        this.dataMessage = new JsonDataMessage(c.getDataMessage().get(), username);
      }

      if (c.getSyncMessage().isPresent()) {
        this.syncMessage = new JsonSyncMessage(c.getSyncMessage().get(), username);
      }

      if (c.getCallMessage().isPresent()) {
        this.callMessage = new JsonCallMessage(c.getCallMessage().get());
      }

      if (c.getReceiptMessage().isPresent()) {
        this.receipt = new JsonReceiptMessage(c.getReceiptMessage().get());
      }

      if (c.getTypingMessage().isPresent()) {
        this.typing = new JsonTypingMessage(c.getTypingMessage().get());
      }
    }
    isUnidentifiedSender = envelope.isUnidentifiedSender();
  }

  private static String formatTimestampISO(long timestamp) {
    Date date = new Date(timestamp);
    final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    return df.format(date);
  }
}
