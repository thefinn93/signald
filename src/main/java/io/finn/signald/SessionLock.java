/*
 * Copyright (C) 2021 Finn Herzfeld
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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.whispersystems.signalservice.api.SignalSessionLock;

public class SessionLock implements SignalSessionLock {
  private static final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
  private UUID account;

  public SessionLock(UUID a) { account = a; }

  @Override
  public Lock acquire() {
    ReentrantLock lock = null;
    synchronized (locks) {
      lock = locks.get(account.toString());
      if (lock == null) {
        lock = new ReentrantLock();
        locks.put(account.toString(), lock);
      }
    }
    lock.lock();
    return lock::unlock;
  }
}
