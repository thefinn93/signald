/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.finn.signald.annotations.Doc;
import io.finn.signald.clientprotocol.v1.exceptions.InternalError;
import io.finn.signald.clientprotocol.v1.exceptions.InvalidProxyError;
import io.finn.signald.clientprotocol.v1.exceptions.NoSuchAccountError;
import io.finn.signald.clientprotocol.v1.exceptions.ServerNotFoundError;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.util.Base64;

@Doc("represents a file attached to a message. When sending, only `filename` is required.")
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

  public JsonAttachment(SignalServiceAttachment attachment, ACI aci) throws InternalError, InvalidProxyError, ServerNotFoundError, NoSuchAccountError {
    this(attachment);
    if (attachment.isPointer()) {
      File file = Common.getManager(aci).getAttachmentFile(id);
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

  public SignalServiceAttachmentStream asStream() throws IOException {
    File attachmentFile = new File(filename);
    InputStream attachmentStream = new FileInputStream(attachmentFile);
    final long attachmentSize = attachmentFile.length();
    if (contentType == null) {
      contentType = Files.probeContentType(attachmentFile.toPath());
      if (contentType == null) {
        contentType = "application/octet-stream";
      }
    }
    return new SignalServiceAttachmentStream(
        attachmentStream, contentType, attachmentSize, customFilename == null ? Optional.fromNullable(attachmentFile.getName()) : Optional.of(customFilename), voiceNote, false,
        false, getPreview(), width, height, System.currentTimeMillis(), Optional.fromNullable(caption), Optional.fromNullable(blurhash), null, null, Optional.absent());
  }
}
