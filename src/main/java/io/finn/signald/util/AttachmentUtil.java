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
