/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v0;

import io.finn.signald.JsonCallMessage;
import io.finn.signald.JsonReceiptMessage;
import io.finn.signald.JsonTypingMessage;
import io.finn.signald.Manager;
import io.finn.signald.annotations.Deprecated;
import io.finn.signald.annotations.Doc;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

@Deprecated(1641027661)
public class JsonMessageEnvelope {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) public String username;
  @ExampleValue(ExampleValue.LOCAL_UUID) public String uuid;
  public JsonAddress source;
  public int sourceDevice;
  public String type;
  @Doc("this field is no longer available and will never be populated") public String relay;
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

  public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent c, ACI aci)
      throws IOException, SQLException, NoSuchAccountException, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    this.username = Database.Get().AccountsTable.getE164(aci);

    if (envelope.hasServerGuid()) {
      uuid = envelope.getServerGuid();
    }

    Manager m = Manager.get(aci);
    if (!envelope.isUnidentifiedSender()) {
      source = new JsonAddress(Database.Get(m.getACI()).RecipientsTable.get(envelope.getSourceAddress()).getAddress());
    } else if (c != null) {
      source = new JsonAddress(Database.Get(m.getACI()).RecipientsTable.get(c.getSender()).getAddress());
    }

    if (envelope.hasSourceDevice()) {
      sourceDevice = envelope.getSourceDevice();
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
        this.dataMessage = new JsonDataMessage(c.getDataMessage().get(), aci);
      }

      if (c.getSyncMessage().isPresent()) {
        this.syncMessage = new JsonSyncMessage(c.getSyncMessage().get(), aci);
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
