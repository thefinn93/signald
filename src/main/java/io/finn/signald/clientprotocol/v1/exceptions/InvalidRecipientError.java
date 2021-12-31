/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.clientprotocol.v1.exceptions;

public class InvalidRecipientError extends ExceptionWrapper {
  public InvalidRecipientError() { super("request must specify either recipientGroupId or recipientAddress"); }
}
