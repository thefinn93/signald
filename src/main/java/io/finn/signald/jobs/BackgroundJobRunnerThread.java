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

package io.finn.signald.jobs;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BackgroundJobRunnerThread implements Runnable {
  private static final Logger logger = LogManager.getLogger();
  private static final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();

  public static void queue(Job job) { queue.add(job); }

  @Override
  public void run() {
    while (true) {
      Job job;
      try {
        job = queue.take();
      } catch (InterruptedException e) {
        logger.catching(e);
        break;
      }

      logger.debug("running job " + job.getClass().getName());
      try {
        job.run();
      } catch (Throwable t) {
        logger.warn("error running" + job.getClass().getName());
        logger.debug(t);
      }
    }
  }
}
