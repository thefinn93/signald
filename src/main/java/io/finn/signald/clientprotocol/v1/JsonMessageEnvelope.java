/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import io.finn.signald.JsonCallMessage;
import io.finn.signald.JsonReceiptMessage;
import io.finn.signald.JsonTypingMessage;
import io.finn.signald.Manager;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.AccountsTable;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

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

  public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent c, ACI aci) throws NoSuchAccountError, InternalError, ServerNotFoundError, InvalidProxyError {
    try {
      this.username = AccountsTable.getE164(aci);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    } catch (SQLException e) {
      throw new InternalError("error looking up username for envelope", e);
    }

    if (envelope.hasServerGuid()) {
      uuid = envelope.getServerGuid();
    }

    Manager m = Common.getManager(aci);
    if (!envelope.isUnidentifiedSender()) {
      source = new JsonAddress(Common.getRecipient(m.getRecipientsTable(), envelope.getSourceAddress()));
    } else if (c != null) {
      source = new JsonAddress(Common.getRecipient(m.getRecipientsTable(), (c.getSender())));
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
