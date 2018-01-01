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
