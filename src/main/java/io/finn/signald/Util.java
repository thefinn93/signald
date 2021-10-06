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

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import org.whispersystems.util.Base64;

public class Util {
  public static String getSecret(int size) {
    byte[] secret = getSecretBytes(size);
    return Base64.encodeBytes(secret);
  }

  public static byte[] getSecretBytes(int size) {
    byte[] secret = new byte[size];
    getSecureRandom().nextBytes(secret);
    return secret;
  }

  private static SecureRandom getSecureRandom() {
    try {
      return SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public static File createTempFile() throws IOException { return File.createTempFile("signald_tmp_", ".tmp"); }

  public static String redact(UUID uuid) { return redact(uuid.toString()); }
  public static String redact(String in) {
    if (in == null) {
      return "[null]";
    }
    if (in.length() < 2) {
      return new String(new char[in.length()]).replace("\0", "*");
    }
    int unredactAfter = in.length() - 2;
    return new String(new char[unredactAfter]).replace("\0", "*") + in.substring(unredactAfter);
  }

  public static void copyStream(InputStream input, OutputStream output, int bufferSize) throws IOException {
    byte[] buffer = new byte[bufferSize];
    int read;

    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }

  public static byte[] readFully(InputStream in) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Util.copyStream(in, baos);
    return baos.toByteArray();
  }

  public static void copyStream(InputStream input, OutputStream output) throws IOException { copyStream(input, output, 4096); }
}
