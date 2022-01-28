/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
 */

package io.finn.signald;

import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.whispersystems.signalservice.api.SignalSessionLock;

public class SessionLock implements SignalSessionLock {
  private final static Logger logger = LogManager.getLogger();
  private static final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private final Account account;

  public SessionLock(Account a) { account = a; }

  @Override
  public Lock acquire() {
    ReentrantLock lock;
    String key = account.getACI().toString();
    synchronized (locks) {
      lock = locks.get(key);
      if (lock == null) {
        logger.debug("first lock acquision for this account");
        lock = new ReentrantLock();
        locks.put(key, lock);
      }
    }
    logger.debug("acquiring session lock (held by current thread: {}, held by {}, queue length {})", lock.isHeldByCurrentThread(), lock.getHoldCount(), lock.getQueueLength());
    long start = System.currentTimeMillis();
    lock.lock();
    long acquireTime = System.currentTimeMillis() - start;
    logger.debug("session lock acquired for account {} in {} ms (held by {}, queue length {})", Util.redact(account.getACI()), acquireTime, lock.getHoldCount(),
                 lock.getQueueLength());
    return lock::unlock;
  }
}
