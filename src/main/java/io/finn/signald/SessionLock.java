/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.whispersystems.signalservice.api.SignalSessionLock;

public class SessionLock implements SignalSessionLock {
  private static final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private Account account;

  public SessionLock(Account a) { account = a; }

  @Override
  public Lock acquire() {
    ReentrantLock lock;
    synchronized (locks) {
      lock = locks.get(account.getUUID().toString());
      if (lock == null) {
        lock = new ReentrantLock();
        locks.put(account.toString(), lock);
      }
    }
    lock.lock();
    return lock::unlock;
  }
}
