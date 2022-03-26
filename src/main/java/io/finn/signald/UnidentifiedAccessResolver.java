/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public interface UnidentifiedAccessResolver {
  List<Optional<UnidentifiedAccessPair>> getAccessFor(Collection<SignalServiceAddress> recipients);
}
