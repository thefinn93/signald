/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalSessionLock;

public class SessionLock implements SignalSessionLock {
  private final static Logger logger = LogManager.getLogger();
  private static final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private final Account account;
  private ReentrantLock lock = null;

  public SessionLock(Account a) { account = a; }

  @Override
  public Lock acquire() {
    synchronized (locks) {
      lock = locks.get(account.getUUID().toString());
      if (lock == null) {
        lock = new ReentrantLock();
        locks.put(account.toString(), lock);
      }
    }
    lock.lock();
    logger.debug("session lock acquired for account {}", Util.redact(account.getACI()));
    return this::unlock;
  }

  void unlock() {
    if (lock != null) {
      logger.debug("session lock released for account {}", Util.redact(account.getACI()));
      lock.unlock();
    } else {
      logger.debug("releasing null lock? this should never happen");
    }
  }
}
