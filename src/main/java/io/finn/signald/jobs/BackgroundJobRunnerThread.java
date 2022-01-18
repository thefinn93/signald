/*
 * Copyright 2022 signald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 * See included LICENSE file
 *
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

      logger.debug("running job {}", job.getClass().getName());
      try {
        job.run();
      } catch (Throwable t) {
        logger.warn("error running {}", job.getClass().getName());
        logger.debug(t);
      }
    }
  }
}
