/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.exceptions;

public class UnknownGroupException extends Exception {
  public UnknownGroupException() { super("unknown group requested"); }
}