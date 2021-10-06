/*
 * // Copyright 2021 signald contributors
 * // SPDX-License-Identifier: GPL-3.0-only
 * // See included LICENSE file
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.io.File;
import java.util.UUID;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.util.Base64;

@Doc("represents a file attached to a message. When seding, only `filename` is required.")
public class JsonAttachment {
  public String contentType;
  public String id;
  public int size;
  @Doc("when receiving, the path that file has been downloaded to") public String storedFilename;
  @Doc("when sending, the path to the local file to upload") public String filename;
  @Doc("the original name of the file") public String customFilename;
  public String caption;
  public int width;
  public int height;
  public boolean voiceNote;
  public String preview;
  public String key;
  public String digest;
  public String blurhash;

  JsonAttachment() {}

  JsonAttachment(String storedFilename) { this.filename = storedFilename; }

  public JsonAttachment(SignalServiceAttachment attachment) {
    this.contentType = attachment.getContentType();
    final SignalServiceAttachmentPointer pointer = attachment.asPointer();
    if (attachment.isPointer()) {
      // unclear if this is the correct identifier or the right way to be storing attachments anymore
      this.id = pointer.getRemoteId().toString();
      this.key = Base64.encodeBytes(pointer.getKey());

      if (pointer.getSize().isPresent()) {
        this.size = pointer.getSize().get();
      }

      if (pointer.getPreview().isPresent()) {
        this.preview = Base64.encodeBytes(pointer.getPreview().get());
      }

      if (pointer.getDigest().isPresent()) {
        this.digest = Base64.encodeBytes(pointer.getDigest().get());
      }

      this.voiceNote = pointer.getVoiceNote();

      this.width = pointer.getWidth();
      this.height = pointer.getHeight();

      if (pointer.getCaption().isPresent()) {
        this.caption = pointer.getCaption().get();
      }

      if (pointer.getBlurHash().isPresent()) {
        this.blurhash = pointer.getBlurHash().get();
      }

      if (pointer.getFileName().isPresent()) {
        this.customFilename = pointer.getFileName().get();
      }
    }
  }

  public JsonAttachment(SignalServiceAttachment attachment, UUID accountUUID) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    this(attachment);
    if (attachment.isPointer()) {
      File file = Common.getManager(accountUUID).getAttachmentFile(id);
      if (file.exists()) {
        this.storedFilename = file.toString();
      }
    }
  }

  @JsonIgnore
  public Optional<byte[]> getPreview() {
    if (preview != null) {
      return Optional.of(Base64.encodeBytesToBytes(preview.getBytes()));
    }
    return Optional.absent();
  }
}
