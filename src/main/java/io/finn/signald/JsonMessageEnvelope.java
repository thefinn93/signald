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
import org.whispersystems.signalservice.internal.util.Base64;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import io.finn.signald.Manager;

class JsonMessageEnvelope {
    String username;
    String uuid;
    boolean hasUuid;
    boolean hasSource;
    String source;
    boolean hasSourceDevice;
    int sourceDevice;
    int type;
    boolean hasRelay;
    String relay;
    long timestamp;
    String timestampISO;
    long serverTimestamp;
    boolean hasLegacyMessage;
    boolean hasContent;
    // String content;
    boolean isSignalMessage;
    boolean isPrekeySignalMessage;
    boolean isReceipt;
    boolean isUnidentifiedSender;
    JsonDataMessage dataMessage;
    JsonSyncMessage syncMessage;
    JsonCallMessage callMessage;


    public JsonMessageEnvelope(SignalServiceEnvelope envelope, SignalServiceContent c, Manager m) {
        SignalServiceAddress sourceAddress = envelope.getSourceAddress();
        username = m.getUsername();
        hasUuid = envelope.hasUuid();
        if(hasUuid) {
          uuid = envelope.getUuid();
        }
        hasSource = envelope.hasSource();
        if(hasSource) {
          source = sourceAddress.getNumber();
        }
        hasSourceDevice = envelope.hasSourceDevice();
        if(hasSourceDevice) {
          sourceDevice = envelope.getSourceDevice();
        }
        type = envelope.getType();
        hasRelay = sourceAddress.getRelay().isPresent();
        if(hasRelay) {
            relay = sourceAddress.getRelay().get();
        }
        timestamp = envelope.getTimestamp();
        timestampISO = formatTimestampISO(envelope.getTimestamp());
        serverTimestamp = envelope.getServerTimestamp();
        hasLegacyMessage = envelope.hasLegacyMessage();
        hasContent = envelope.hasContent();
        // if(hasContent) {
        //   content = Base64.encodeBytes(envelope.getContent());
        // }
        isReceipt = envelope.isReceipt();
        if (c != null) {
            if (c.getDataMessage().isPresent()) {
                this.dataMessage = new JsonDataMessage(c.getDataMessage().get(), m);
            }
            if (c.getSyncMessage().isPresent()) {
                this.syncMessage = new JsonSyncMessage(c.getSyncMessage().get());
            }
            if (c.getCallMessage().isPresent()) {
                this.callMessage = new JsonCallMessage(c.getCallMessage().get());
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
