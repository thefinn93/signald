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

import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

class JsonMessageEnvelope {
    String username;
    String uuid;
    String source;
    int sourceDevice;
    int type;
    String relay;
    long timestamp;
    String timestampISO;
    long serverTimestamp;
    boolean hasLegacyMessage;
    boolean hasContent;
    // String content;
    boolean isReceipt;
    boolean isUnidentifiedSender;
    JsonDataMessage dataMessage;
    JsonSyncMessage syncMessage;
    JsonCallMessage callMessage;
    JsonReceiptMessage receipt;
    JsonTypingMessage typing;


    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent c, String username) throws IOException, NoSuchAccountException {
        SignalServiceAddress sourceAddress = envelope.getSourceAddress();
        this.username = username;

        if (envelope.hasUuid()) {
            uuid = envelope.getUuid();
        }

        if (envelope.hasSource()) {
            source = sourceAddress.getNumber();
        } else {
            source = c.getSender();
        }

        if (envelope.hasSourceDevice()) {
            sourceDevice = envelope.getSourceDevice();
        }

        type = envelope.getType();

        if (sourceAddress.getRelay().isPresent()) {
            relay = sourceAddress.getRelay().get();
        }

        timestamp = envelope.getTimestamp();
        timestampISO = formatTimestampISO(envelope.getTimestamp());
        serverTimestamp = envelope.getServerTimestamp();
        hasLegacyMessage = envelope.hasLegacyMessage();
        hasContent = envelope.hasContent();
        isReceipt = envelope.isReceipt();

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
