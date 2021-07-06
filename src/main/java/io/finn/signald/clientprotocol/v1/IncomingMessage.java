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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.finn.signald.Manager;
import io.finn.signald.annotations.ExampleValue;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyException;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccount;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
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
  @JsonProperty("server_guid") public String serverGuid;

  public IncomingMessage(SignalServiceEnvelope envelope, SignalServiceContent content, String a)
      throws SQLException, IOException, NoSuchAccount, InvalidKeyException, ServerNotFoundException, InvalidProxyException {
    account = a;

    if (envelope.hasServerGuid()) {
      serverGuid = envelope.getServerGuid();
    }

    Manager m = Utils.getManager(account);
    if (envelope.hasSource()) {
      source = new JsonAddress(m.getResolver().resolve(envelope.getSourceAddress()));
    } else if (content != null) {
      source = new JsonAddress(m.getResolver().resolve(content.getSender()));
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
        this.dataMessage = new JsonDataMessage(content.getDataMessage().get(), account);
      }

      if (content.getSyncMessage().isPresent()) {
        this.syncMessage = new JsonSyncMessage(content.getSyncMessage().get(), account);
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
    }
    unidentifiedSender = envelope.isUnidentifiedSender();
  }
}
