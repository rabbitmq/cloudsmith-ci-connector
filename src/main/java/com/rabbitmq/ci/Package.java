/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import java.time.ZonedDateTime;

class Package {

  private String self_url;
  private boolean is_sync_completed;
  private boolean is_sync_failed;
  private String filename;
  private String cdn_url;
  private String version;
  private String checksum_sha256;
  private String status_reason;
  private ZonedDateTime uploaded_at;

  String selfUrl() {
    return this.self_url;
  }

  boolean isSyncCompleted() {
    return this.is_sync_completed;
  }

  boolean isSyncFailed() {
    return this.is_sync_failed;
  }

  String filename() {
    return this.filename;
  }

  String cdnUrl() {
    return this.cdn_url;
  }

  String version() {
    return this.version;
  }

  String sha256() {
    return this.checksum_sha256;
  }

  String statusReason() {
    return this.status_reason;
  }

  public ZonedDateTime uploadedAt() {
    return uploaded_at;
  }

  void setIs_sync_completed(boolean is_sync_completed) {
    this.is_sync_completed = is_sync_completed;
  }

  void setIs_sync_failed(boolean is_sync_failed) {
    this.is_sync_failed = is_sync_failed;
  }

  void setVersion(String version) {
    this.version = version;
  }

  void setFilename(String filename) {
    this.filename = filename;
  }

  public void setUploaded_at(ZonedDateTime uploaded_at) {
    this.uploaded_at = uploaded_at;
  }

  @Override
  public String toString() {
    return "Package{"
        + "self_url='"
        + self_url
        + '\''
        + ", is_sync_completed="
        + is_sync_completed
        + ", filename='"
        + filename
        + '\''
        + ", cdn_url='"
        + cdn_url
        + '\''
        + ", version='"
        + version
        + '\''
        + ", checksum_sha256='"
        + checksum_sha256
        + '\''
        + '}';
  }

  /*

  {
    "self_url": "https://api.cloudsmith.io/v1/packages/rabbitmq/concourse-resource-test/IvErs7CXiLLs/",
    "stage": 9,
    "stage_str": "Fully Synchronised",
    "stage_updated_at": "2021-03-19T12:58:11.418817Z",
    "status": 4,
    "status_reason": null,
    "status_str": "Completed",
    "status_updated_at": "2021-03-19T12:58:11.418799Z",
    "is_sync_awaiting": false,
    "is_sync_completed": true,
    "is_sync_failed": false,
    "is_sync_in_flight": false,
    "is_sync_in_progress": false,
    "sync_finished_at": "2021-03-19T12:58:11.418812Z",
    "sync_progress": 100,
    "architectures": [
      {
        "name": "all",
        "description": null
      }
    ],
    "checksum_md5": "bd92285e217eb8f4ea40faacddf7f0e1",
    "checksum_sha1": "bb62292d401a666e373ba8e772fae80207015ff3",
    "checksum_sha256": "9caade9720a63aa4ad127acbe6cd75c369ee46dfb1ea2d8192fc1225aeb7ba2c",
    "checksum_sha512": "76d0d03bfa30fe7ef15ecb1938422f0e61a787e4b872d7f8ed8d198151aa40a8fe1d81dc1027008b4e67fd556c667790698d27037d85d3f1cbbe0106eec4ccdb",
    "dependencies_checksum_md5": "bebdeb15bacdcb24d3631fdf25994ecc",
    "description": "This package is a dummy package which will install all Erlang/OTP  applications which use graphical interface and therefore require  X Window System to run.",
    "distro": {
      "slug": "ubuntu",
      "name": "Ubuntu",
      "variants": "",
      "self_url": "https://api.cloudsmith.io/v1/distros/ubuntu/"
    },
    "distro_version": {
      "slug": "focal",
      "name": "20.04 Focal Fossa"
    },
    "downloads": 0,
    "cdn_url": "https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/deb/ubuntu/pool/focal/main/e/er/erlang-x11_23.2.7-1_all.deb",
    "epoch": 1,
    "extension": ".deb",
    "filename": "erlang-x11_23.2.7-1_all.deb",
    "files": [
      {
        "checksum_md5": "bd92285e217eb8f4ea40faacddf7f0e1",
        "checksum_sha1": "bb62292d401a666e373ba8e772fae80207015ff3",
        "checksum_sha256": "9caade9720a63aa4ad127acbe6cd75c369ee46dfb1ea2d8192fc1225aeb7ba2c",
        "checksum_sha512": "76d0d03bfa30fe7ef15ecb1938422f0e61a787e4b872d7f8ed8d198151aa40a8fe1d81dc1027008b4e67fd556c667790698d27037d85d3f1cbbe0106eec4ccdb",
        "cdn_url": "https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/deb/ubuntu/pool/focal/main/e/er/erlang-x11_23.2.7-1_all.deb",
        "downloads": 0,
        "filename": "erlang-x11_23.2.7-1_all.deb",
        "is_downloadable": true,
        "is_primary": true,
        "is_synchronised": true,
        "signature_url": "https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/IvErs7CXiLLs/gpg.TxENxafR5s5d.asc",
        "size": 48168,
        "slug_perm": "TxENxafR5s5d",
        "tag": "pkg"
      }
    ],
    "format": "deb",
    "format_url": "https://api.cloudsmith.io/v1/formats/deb/",
    "identifier_perm": "IvErs7CXiLLs",
    "indexed": true,
    "license": null,
    "name": "erlang-x11",
    "namespace": "rabbitmq",
    "namespace_url": "https://api.cloudsmith.io/v1/namespaces/rabbitmq/",
    "num_files": 11,
    "package_type": 1,
    "release": "1",
    "repository": "concourse-resource-test",
    "repository_url": "https://api.cloudsmith.io/v1/repos/rabbitmq/concourse-resource-test/",
    "security_scan_status": "Awaiting Security Scan",
    "security_scan_status_updated_at": null,
    "security_scan_started_at": null,
    "security_scan_completed_at": null,
    "self_html_url": "https://cloudsmith.io/~rabbitmq/repos/concourse-resource-test/packages/detail/deb/erlang-x11/1:23.2.7-1/a=all;d=ubuntu%252Ffocal;t=binary/",
    "status_url": "https://api.cloudsmith.io/v1/packages/rabbitmq/concourse-resource-test/IvErs7CXiLLs/status/",
    "signature_url": "https://dl.cloudsmith.io/public/rabbitmq/concourse-resource-test/IvErs7CXiLLs/gpg.TxENxafR5s5d.asc",
    "size": 48168,
    "slug": "erlang-x11_2327-1_alldeb-nVQ",
    "slug_perm": "IvErs7CXiLLs",
    "subtype": null,
    "summary": "Erlang/OTP applications that require X Window System",
    "tags": {
      "info": [
        "erlang",
        "erlang-23"
      ],
      "version": [
        "latest"
      ]
    },
    "tags_immutable": {},
    "type_display": "ubuntu/focal",
    "uploaded_at": "2021-03-19T12:58:06.413956Z",
    "uploader": "team-rabbitmq",
    "uploader_url": "https://api.cloudsmith.io/v1/users/profile/team-rabbitmq/",
    "version": "1:23.2.7-1",
    "version_orig": "1:23.2.7-1",
    "vulnerability_scan_results_url": "https://api.cloudsmith.io/v1/vulnerabilities/rabbitmq/concourse-resource-test/IvErs7CXiLLs/"
  }

   */

}
