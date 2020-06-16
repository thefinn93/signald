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

import java.io.File;
import java.io.IOException;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.util.Base64;
import org.whispersystems.libsignal.util.guava.Optional;


class JsonAttachment {
    String contentType;
    long id;
    int size;
    String storedFilename;
    String filename;
    String caption;
    int width;
    int height;
    boolean voiceNote;
    String preview;
    String key;
    String digest;
    String blurhash;

    JsonAttachment() {}

    JsonAttachment(String storedFilename) {
        this.filename = storedFilename;
    }

    JsonAttachment(SignalServiceAttachment attachment, String username) throws IOException, NoSuchAccountException {
        this.contentType = attachment.getContentType();
        final SignalServiceAttachmentPointer pointer = attachment.asPointer();
        if (attachment.isPointer()) {
            this.id = pointer.getId();
            this.key = Base64.encodeBytes(pointer.getKey());

            if (pointer.getSize().isPresent()) {
                this.size = pointer.getSize().get();
            }

            if(pointer.getPreview().isPresent()) {
                this.preview = Base64.encodeBytes(pointer.getPreview().get());
            }

            if(pointer.getDigest().isPresent()) {
                this.digest = Base64.encodeBytes(pointer.getDigest().get());
            }

            this.voiceNote = pointer.getVoiceNote();

            this.width = pointer.getWidth();
            this.height = pointer.getHeight();

            if(pointer.getCaption().isPresent()) {
                this.caption = pointer.getCaption().get();
            }

            if(pointer.getBlurHash().isPresent()) {
                this.blurhash = pointer.getBlurHash().get();
            }

            File file = Manager.get(username).getAttachmentFile(pointer.getId());
            if(file.exists()) {
                this.storedFilename = file.toString();
            }
        }
    }

    public Optional<byte[]> getPreview() {
      if(preview != null) {
          return Optional.of(Base64.encodeBytesToBytes(preview.getBytes()));
      }
      return Optional.absent();
    }
}
