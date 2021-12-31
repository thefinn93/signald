/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald.util;

public class MutableLong {
  private Long value = null;

  public Long getValue() { return value; }

  public void setValue(Long value) { this.value = value; }
}
