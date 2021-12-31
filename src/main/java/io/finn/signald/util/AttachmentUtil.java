/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.whispersystems.signalservice.api.util.StreamDetails;

public class AttachmentUtil {
  public static StreamDetails createStreamDetailsFromFile(File file) throws IOException {
    InputStream stream = new FileInputStream(file);
    final long size = file.length();
    String mime = Files.probeContentType(file.toPath());
    if (mime == null) {
      mime = "application/octet-stream";
    }
    return new StreamDetails(stream, mime, size);
  }
}
