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

import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import io.finn.signald.Manager;

class JsonMessageEnvelope {
    String source;
    String username;
    int sourceDevice;
    String relay;
    long timestamp;
    String timestampISO;
    boolean isReceipt;
    JsonDataMessage dataMessage;
    JsonSyncMessage syncMessage;
    JsonCallMessage callMessage;

    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent content, Manager m) {
        SignalServiceAddress source = envelope.getSourceAddress();
        this.source = source.getNumber();
        this.sourceDevice = envelope.getSourceDevice();
        this.relay = source.getRelay().isPresent() ? source.getRelay().get() : null;
        this.timestamp = envelope.getTimestamp();
        this.timestampISO = formatTimestampISO(envelope.getTimestamp());
        this.isReceipt = envelope.isReceipt();
        this.username = m.getUsername();
        if (content != null) {
            if (content.getDataMessage().isPresent()) {
                this.dataMessage = new JsonDataMessage(content.getDataMessage().get(), m);
            }
            if (content.getSyncMessage().isPresent()) {
                this.syncMessage = new JsonSyncMessage(content.getSyncMessage().get());
            }
            if (content.getCallMessage().isPresent()) {
                this.callMessage = new JsonCallMessage(content.getCallMessage().get());
            }
        }
    }

    private static String formatTimestampISO(long timestamp) {
        Date date = new Date(timestamp);
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(date);
    }

}
