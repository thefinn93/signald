package io.finn.signald;

import java.io.File;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;

class JsonAttachment {
    String contentType;
    long id;
    int size;
    String storedFilename;

    JsonAttachment(SignalServiceAttachment attachment, Manager m) {
        this.contentType = attachment.getContentType();
        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = pointer.getId();
            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }
            if( m != null) {
                File file = m.getAttachmentFile(pointer.getId());
                if( file.exists()) {
                    this.storedFilename = file.toString();
                }
            }

        }
    }
}
