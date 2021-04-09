package com.rabbitmq.concourse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

public class JsonTest {

  private static final String JSON =
      "{\n"
          + "    \"self_url\": \"https://api.cloudsmith.io/v1/packages/rabbitmq/concourse-resource-test/IvErs7CXiLLs/\",\n"
          + "    \"stage\": 9,\n"
          + "    \"stage_str\": \"Fully Synchronised\",\n"
          + "    \"stage_updated_at\": \"2021-03-19T12:58:11.418817Z\",\n"
          + "    \"status\": 4,\n"
          + "    \"status_reason\": null,\n"
          + "    \"status_str\": \"Completed\",\n"
          + "    \"status_updated_at\": \"2021-03-19T12:58:11.418799Z\",\n"
          + "    \"is_sync_awaiting\": false,\n"
          + "    \"is_sync_completed\": true,\n"
          + "    \"is_sync_failed\": false,\n"
          + "    \"is_sync_in_flight\": false,\n"
          + "    \"is_sync_in_progress\": false,\n"
          + "    \"sync_finished_at\": \"2021-03-19T12:58:11.418812Z\",\n"
          + "    \"sync_progress\": 100,\n"
          + "    \"architectures\": [\n"
          + "      {\n"
          + "        \"name\": \"all\",\n"
          + "        \"description\": null\n"
          + "      }\n"
          + "    ],\n"
          + "    \"checksum_md5\": \"bd92285e217eb8f4ea40faacddf7f0e1\",\n"
          + "    \"checksum_sha1\": \"bb62292d401a666e373ba8e772fae80207015ff3\",\n"
          + "    \"checksum_sha256\": \"9caade9720a63aa4ad127acbe6cd75c369ee46dfb1ea2d8192fc1225aeb7ba2c\",\n"
          + "    \"checksum_sha512\": \"76d0d03bfa30fe7ef15ecb1938422f0e61a787e4b872d7f8ed8d198151aa40a8fe1d81dc1027008b4e67fd556c667790698d27037d85d3f1cbbe0106eec4ccdb\",\n"
          + "    \"dependencies_checksum_md5\": \"bebdeb15bacdcb24d3631fdf25994ecc\",\n"
          + "    \"description\": \"This package is a dummy package which will install all Erlang/OTP  applications which use graphical interface and therefore require  X Window System to run.\",\n"
          + "    \"distro\": {\n"
          + "      \"slug\": \"ubuntu\",\n"
          + "      \"name\": \"Ubuntu\",\n"
          + "      \"variants\": \"\",\n"
          + "      \"self_url\": \"https://api.cloudsmith.io/v1/distros/ubuntu/\"\n"
          + "    },\n"
          + "    \"distro_version\": {\n"
          + "      \"slug\": \"focal\",\n"
          + "      \"name\": \"20.04 Focal Fossa\"\n"
          + "    },\n"
          + "    \"downloads\": 0,\n"
          + "    \"cdn_url\": \"https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/deb/ubuntu/pool/focal/main/e/er/erlang-x11_23.2.7-1_all.deb\",\n"
          + "    \"epoch\": 1,\n"
          + "    \"extension\": \".deb\",\n"
          + "    \"filename\": \"erlang-x11_23.2.7-1_all.deb\",\n"
          + "    \"files\": [\n"
          + "      {\n"
          + "        \"checksum_md5\": \"bd92285e217eb8f4ea40faacddf7f0e1\",\n"
          + "        \"checksum_sha1\": \"bb62292d401a666e373ba8e772fae80207015ff3\",\n"
          + "        \"checksum_sha256\": \"9caade9720a63aa4ad127acbe6cd75c369ee46dfb1ea2d8192fc1225aeb7ba2c\",\n"
          + "        \"checksum_sha512\": \"76d0d03bfa30fe7ef15ecb1938422f0e61a787e4b872d7f8ed8d198151aa40a8fe1d81dc1027008b4e67fd556c667790698d27037d85d3f1cbbe0106eec4ccdb\",\n"
          + "        \"cdn_url\": \"https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/deb/ubuntu/pool/focal/main/e/er/erlang-x11_23.2.7-1_all.deb\",\n"
          + "        \"downloads\": 0,\n"
          + "        \"filename\": \"erlang-x11_23.2.7-1_all.deb\",\n"
          + "        \"is_downloadable\": true,\n"
          + "        \"is_primary\": true,\n"
          + "        \"is_synchronised\": true,\n"
          + "        \"signature_url\": \"https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/IvErs7CXiLLs/gpg.TxENxafR5s5d.asc\",\n"
          + "        \"size\": 48168,\n"
          + "        \"slug_perm\": \"TxENxafR5s5d\",\n"
          + "        \"tag\": \"pkg\"\n"
          + "      }\n"
          + "    ],\n"
          + "    \"format\": \"deb\",\n"
          + "    \"format_url\": \"https://api.cloudsmith.io/v1/formats/deb/\",\n"
          + "    \"identifier_perm\": \"IvErs7CXiLLs\",\n"
          + "    \"indexed\": true,\n"
          + "    \"license\": null,\n"
          + "    \"name\": \"erlang-x11\",\n"
          + "    \"namespace\": \"rabbitmq\",\n"
          + "    \"namespace_url\": \"https://api.cloudsmith.io/v1/namespaces/rabbitmq/\",\n"
          + "    \"num_files\": 11,\n"
          + "    \"package_type\": 1,\n"
          + "    \"release\": \"1\",\n"
          + "    \"repository\": \"concourse-resource-test\",\n"
          + "    \"repository_url\": \"https://api.cloudsmith.io/v1/repos/rabbitmq/concourse-resource-test/\",\n"
          + "    \"security_scan_status\": \"Awaiting Security Scan\",\n"
          + "    \"security_scan_status_updated_at\": null,\n"
          + "    \"security_scan_started_at\": null,\n"
          + "    \"security_scan_completed_at\": null,\n"
          + "    \"self_html_url\": \"https://cloudsmith.io/~rabbitmq/repos/concourse-resource-test/packages/detail/deb/erlang-x11/1:23.2.7-1/a=all;d=ubuntu%252Ffocal;t=binary/\",\n"
          + "    \"status_url\": \"https://api.cloudsmith.io/v1/packages/rabbitmq/concourse-resource-test/IvErs7CXiLLs/status/\",\n"
          + "    \"signature_url\": \"https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/IvErs7CXiLLs/gpg.TxENxafR5s5d.asc\",\n"
          + "    \"size\": 48168,\n"
          + "    \"slug\": \"erlang-x11_2327-1_alldeb-nVQ\",\n"
          + "    \"slug_perm\": \"IvErs7CXiLLs\",\n"
          + "    \"subtype\": null,\n"
          + "    \"summary\": \"Erlang/OTP applications that require X Window System\",\n"
          + "    \"tags\": {\n"
          + "      \"info\": [\n"
          + "        \"erlang\",\n"
          + "        \"erlang-23\"\n"
          + "      ],\n"
          + "      \"version\": [\n"
          + "        \"latest\"\n"
          + "      ]\n"
          + "    },\n"
          + "    \"tags_immutable\": {},\n"
          + "    \"type_display\": \"ubuntu/focal\",\n"
          + "    \"uploaded_at\": \"2021-03-19T12:58:06.413956Z\",\n"
          + "    \"uploader\": \"team-rabbitmq\",\n"
          + "    \"uploader_url\": \"https://api.cloudsmith.io/v1/users/profile/team-rabbitmq/\",\n"
          + "    \"version\": \"1:23.2.7-1\",\n"
          + "    \"version_orig\": \"1:23.2.7-1\",\n"
          + "    \"vulnerability_scan_results_url\": \"https://api.cloudsmith.io/v1/vulnerabilities/rabbitmq/concourse-resource-test/IvErs7CXiLLs/\"\n"
          + "  }";

  @Test
  void jsonPackageShouldDeserialized() {
    Package p = CloudsmithResource.GSON.fromJson(JSON, Package.class);
    assertThat(p.selfUrl())
        .isEqualTo(
            "https://api.cloudsmith.io/v1/packages/rabbitmq/concourse-resource-test/IvErs7CXiLLs/");
    assertThat(p.isSyncCompleted()).isTrue();
    assertThat(p.isSyncFailed()).isFalse();
    assertThat(p.cdnUrl())
        .isEqualTo(
            "https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/deb/ubuntu/pool/focal/main/e/er/erlang-x11_23.2.7-1_all.deb");
    assertThat(p.filename()).isEqualTo("erlang-x11_23.2.7-1_all.deb");
    assertThat(p.version()).isEqualTo("1:23.2.7-1");
    assertThat(p.sha256())
        .isEqualTo("9caade9720a63aa4ad127acbe6cd75c369ee46dfb1ea2d8192fc1225aeb7ba2c");
    assertThat(p.uploadedAt())
        .isEqualTo(
            ZonedDateTime.parse("2021-03-19T12:58:06.413956Z", DateTimeFormatter.ISO_ZONED_DATE_TIME));
  }
}
