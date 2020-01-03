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

import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class JsonSentTranscriptMessage {
    String destination;
    long timestamp;
    long expirationStartTimestamp;
    JsonDataMessage message;
    Map<String, Boolean> unidentifiedStatus = new HashMap<>();
    boolean isRecipientUpdate;

    JsonSentTranscriptMessage(SentTranscriptMessage s, Manager m) {
        if(s.getDestination().isPresent()) {
            destination = s.getDestination().get();
        }

        timestamp = s.getTimestamp();
        expirationStartTimestamp = s.getExpirationStartTimestamp();
        message = new JsonDataMessage(s.getMessage(), m);
        for(String r : s.getRecipients()) {
            unidentifiedStatus.put(r, s.isUnidentified(r));
        }
        isRecipientUpdate = s.isRecipientUpdate(); 
    }
}
