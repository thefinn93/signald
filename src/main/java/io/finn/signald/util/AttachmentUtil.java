/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

import io.finn.signald.Config;
import io.finn.signald.ServiceConfig;
import io.finn.signald.SignalDependencies;
import io.finn.signald.Util;
import io.finn.signald.exceptions.InvalidProxyException;
import io.finn.signald.exceptions.NoSuchAccountException;
import io.finn.signald.exceptions.ServerNotFoundException;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
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
