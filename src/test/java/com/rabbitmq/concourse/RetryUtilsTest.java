/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.concourse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.concourse.RetryUtils.RetryException;
import java.time.Duration;
import java.util.concurrent.Callable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RetryUtilsTest {

  Callable<String> task;

  static <T> T retry(Callable<T> task) throws InterruptedException {
    return RetryUtils.retry(task, Duration.ofMillis(10));
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void init() {
    task = mock(Callable.class);
  }

  @Test
  void shouldReturnResultWhenNoProblem() throws Exception {
    when(task.call()).thenReturn("ok");
    assertThat(retry(task)).isEqualTo("ok");
    verify(task, times(1)).call();
  }

  @Test
  void shouldReturnResultWhenOnlyOneFailure() throws Exception {
    when(task.call()).thenThrow(new Exception()).thenReturn("ok");
    assertThat(retry(task)).isEqualTo("ok");
    verify(task, times(2)).call();
  }

  @Test
  void shouldThrowExceptionWhenRetryExhausted() throws Exception {
    when(task.call()).thenThrow(new Exception());
    Assertions.assertThatThrownBy(() -> retry(task)).isInstanceOf(RetryException.class);
    verify(task, times(3)).call();
  }
}
