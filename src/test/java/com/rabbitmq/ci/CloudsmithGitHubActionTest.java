/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CloudsmithGitHubActionTest {

  @Test
  void testMapSource() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("INPUT_USERNAME", "acogoluegnes");
    env.put("INPUT_ORGANIZATION", "rabbitmq");
    env.put("INPUT_REPOSITORY", "rabbitmq-erlang");
    env.put("INPUT_API_KEY", "the-key");
    env.put("INPUT_NAME", "^rabbitmq-server*");
    env.put("INPUT_TYPE", "deb");
    env.put("INPUT_DISTRIBUTION", "ubuntu/focal");
    env.put("INPUT_ORDER_BY", "time");

    Input.Source source =
        CloudsmithGitHubAction.mapSource(
            k -> env.get(CloudsmithGitHubAction.FIELD_TO_ENVIRONMENT_VARIABLE.apply(k)));
    assertThat(source.username()).isEqualTo("acogoluegnes");
    assertThat(source.organization()).isEqualTo("rabbitmq");
    assertThat(source.repository()).isEqualTo("rabbitmq-erlang");
    assertThat(source.apiKey()).isEqualTo("the-key");
    assertThat(source.name()).isEqualTo("^rabbitmq-server*");
    assertThat(source.type()).isEqualTo("deb");
    assertThat(source.distribution()).isEqualTo("ubuntu/focal");
    assertThat(source.orderByVersion()).isFalse();
  }

  @Test
  void testMapParams() throws Exception {
    Map<String, String> env = new HashMap<>();
    env.put("INPUT_DELETE", "true");
    env.put("INPUT_DO_DELETE", "true");
    env.put("INPUT_REPUBLISH", "true");
    env.put("INPUT_GLOBS", "*.deb");
    env.put("INPUT_TAGS", "erlang,erlang-23.x");
    env.put("INPUT_LOCAL_PATH", "PACKAGES");
    env.put("INPUT_VERSION", "rabbitmq-server-generic-unix-(\\d.*)\\.tar\\.xz");
    env.put("INPUT_VERSION_FILTER", "1:23*");
    env.put("INPUT_KEEP_LAST_N", "2");
    env.put("INPUT_KEEP_LAST_MINOR_PATCHES", "true");

    Input.Params params =
        CloudsmithGitHubAction.mapParams(
            k -> env.get(CloudsmithGitHubAction.FIELD_TO_ENVIRONMENT_VARIABLE.apply(k)));
    assertThat(params.doDelete()).isTrue();
    assertThat(params.republish()).isTrue();
    assertThat(params.globs()).isEqualTo("*.deb");
    assertThat(params.tags()).isEqualTo("erlang,erlang-23.x");
    assertThat(params.localPath()).isEqualTo("PACKAGES");
    assertThat(params.version()).isEqualTo("rabbitmq-server-generic-unix-(\\d.*)\\.tar\\.xz");
    assertThat(params.versionFilter()).isEqualTo("1:23*");
    assertThat(params.keepLastN()).isEqualTo(2);
    assertThat(params.keepLastMinorPatches()).isTrue();
  }
}
