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
