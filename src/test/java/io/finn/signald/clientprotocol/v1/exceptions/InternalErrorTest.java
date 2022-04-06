/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.signal.libsignal.protocol.InvalidKeyException;

public class InternalErrorTest {
  @Test
  @DisplayName("Test that the exception cause shown")
  void TestCause() {
    InternalError err = new InternalError("test", new IOException(new InvalidKeyException("test")));
    Assertions.assertEquals("java.io.IOException", err.exceptions.get(0));
    Assertions.assertEquals("org.signal.libsignal.protocol.InvalidKeyException", err.exceptions.get(1));
    Assertions.assertEquals(2, err.exceptions.size());
  }
}
