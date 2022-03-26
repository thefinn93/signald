/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.*;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import io.finn.signald.db.Database;
import io.finn.signald.exceptions.NoSuchAccountException;
import java.sql.SQLException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

public class IncomingMessage {
  @ExampleValue(ExampleValue.LOCAL_PHONE_NUMBER) public String account;
  public JsonAddress source;
  @JsonProperty("source_device") public int sourceDevice;
  public String type;
  @ExampleValue(ExampleValue.MESSAGE_ID) public long timestamp;
  @JsonProperty("server_receiver_timestamp") @ExampleValue(ExampleValue.MESSAGE_ID) public long serverReceivedTimestamp;
  @JsonProperty("server_deliver_timestamp") @ExampleValue(ExampleValue.MESSAGE_ID) public long serverDeliveredTimestamp;
  @JsonProperty("has_legacy_message") public boolean hasLegacyMessage;
  @JsonProperty("has_content") public boolean hasContent;
  @JsonProperty("unidentified_sender") public boolean unidentifiedSender;
  @JsonProperty("data_message") public JsonDataMessage dataMessage;
  @JsonProperty("sync_message") public JsonSyncMessage syncMessage;
  @JsonProperty("call_message") public CallMessage callMessage;
  @JsonProperty("receipt_message") public ReceiptMessage receiptMessage;
  @JsonProperty("typing_message") public TypingMessage typingMessage;
  @JsonProperty("story_message") public StoryMessage storyMessage;
  @JsonProperty("server_guid") public String serverGuid;

  public IncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content, ACI aci)
      throws NoSuchAccountError, InternalError, ServerNotFoundError, InvalidProxyError, AuthorizationFailedError, SQLException {
    try {
      account = Database.Get().AccountsTable.getE164(aci);
    } catch (NoSuchAccountException e) {
      throw new NoSuchAccountError(e);
    }

    if (envelope.hasServerGuid()) {
      serverGuid = envelope.getServerGuid();
    }

    if (!envelope.isUnidentifiedSender()) {
      source = new JsonAddress(Common.getRecipient(aci, envelope.getSourceAddress()));
    } else if (content != null) {
      source = new JsonAddress(Common.getRecipient(aci, content.getSender()));
    }

    if (envelope.hasSourceDevice()) {
      sourceDevice = envelope.getSourceDevice();
    }

    type = SignalServiceProtos.Envelope.Type.forNumber(envelope.getType()).toString();
    timestamp = envelope.getTimestamp();
    serverReceivedTimestamp = envelope.getServerReceivedTimestamp();
    serverDeliveredTimestamp = envelope.getServerDeliveredTimestamp();
    hasLegacyMessage = envelope.hasLegacyMessage();
    hasContent = envelope.hasContent();

    if (content != null) {
      if (content.getDataMessage().isPresent()) {
        this.dataMessage = new JsonDataMessage(content.getDataMessage().get(), aci);
      }

      if (content.getSyncMessage().isPresent()) {
        this.syncMessage = new JsonSyncMessage(content.getSyncMessage().get(), aci);
      }

      if (content.getCallMessage().isPresent()) {
        this.callMessage = new CallMessage(content.getCallMessage().get());
      }

      if (content.getReceiptMessage().isPresent()) {
        this.receiptMessage = new ReceiptMessage(content.getReceiptMessage().get());
      }

      if (content.getTypingMessage().isPresent()) {
        this.typingMessage = new TypingMessage(content.getTypingMessage().get());
      }

      if (content.getStoryMessage().isPresent()) {
        storyMessage = new StoryMessage(content.getStoryMessage().get(), aci);
      }
    }
    unidentifiedSender = envelope.isUnidentifiedSender();
  }
}
