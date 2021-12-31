/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;
import org.whispersystems.signalservice.api.push.ACI;
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

  public static String redact(ACI aci) { return redact(aci.toString()); }

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
