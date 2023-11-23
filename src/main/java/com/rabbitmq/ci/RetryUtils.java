/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import java.time.Duration;
import java.util.concurrent.Callable;

final class RetryUtils {

  private static final int MAX_ATTEMPTS = 3;
  private static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(5);

  private RetryUtils() {}

  static <T> T retry(Callable<T> task) throws InterruptedException {
    return retry(task, DEFAULT_RETRY_INTERVAL);
  }

  static <T> T retry(Callable<T> task, Duration retryInterval) throws InterruptedException {
    int attempt = 0;
    Exception lastException = null;
    while (++attempt <= MAX_ATTEMPTS) {
      try {
        return task.call();
      } catch (Exception e) {
        lastException = e;
        Thread.sleep(retryInterval.toMillis());
      }
    }
    throw new RetryException("Error while retrying", lastException);
  }

  static class RetryException extends RuntimeException {

    public RetryException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
