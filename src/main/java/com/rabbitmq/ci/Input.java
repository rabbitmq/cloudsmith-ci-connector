/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

class Input {

  private Params params;
  private Source source;
  private Version version;

  Params params() {
    return params;
  }

  Source source() {
    return source;
  }

  Version version() {
    return version;
  }

  Input source(Source source) {
    this.source = source;
    return this;
  }

  Input params(Params params) {
    this.params = params;
    return this;
  }

  Input version(Version version) {
    this.version = version;
    return this;
  }

  @Override
  public String toString() {
    return "Input{" + "params=" + params + ", source=" + source + ", version=" + version + '}';
  }

  // used only for Concourse check and in actions
  static class Version {

    private String version;
    private String distribution;
    private String type;

    public String version() {
      return version;
    }

    public String distribution() {
      return distribution;
    }

    public String type() {
      return type;
    }
  }

  static class Params {

    private boolean delete;
    private boolean do_delete;
    private boolean republish;
    private String globs;
    private String tags;
    private String local_path;
    private String version; // to extract version for "raw" packages when uploading files
    // for deletion
    private String version_filter;
    private int keep_last_n;
    private boolean keep_last_minor_patches = false;

    public String localPath() {
      return local_path;
    }

    public String globs() {
      return globs;
    }

    public String tags() {
      return tags;
    }

    public String versionFilter() {
      return version_filter;
    }

    public boolean delete() {
      return delete;
    }

    public int keepLastN() {
      return keep_last_n;
    }

    public boolean keepLastMinorPatches() {
      return this.keep_last_minor_patches;
    }

    public String version() {
      return version;
    }

    public boolean doDelete() {
      return do_delete;
    }

    public boolean republish() {
      return republish;
    }

    @Override
    public String toString() {
      return "Params{"
          + "globs='"
          + globs
          + '\''
          + ", tags='"
          + tags
          + '\''
          + ", local_path='"
          + local_path
          + '\''
          + '}';
    }
  }

  static class Source {

    private String username;
    private String organization;
    private String repository;
    private String api_key;
    private String name;
    private String type;
    private String distribution;
    private String order_by;

    // TODO add tags to filter out for check?

    public String username() {
      return username;
    }

    public String organization() {
      return organization;
    }

    public String repository() {
      return repository;
    }

    public String apiKey() {
      return api_key;
    }

    public String name() {
      return name;
    }

    public String type() {
      return type;
    }

    public String distribution() {
      return distribution;
    }

    public boolean orderByVersion() {
      return order_by == null || "version".equals(order_by);
    }

    @Override
    public String toString() {
      return "Source{"
          + "username='"
          + username
          + '\''
          + ", organization='"
          + organization
          + '\''
          + ", repository='"
          + repository
          + '\''
          + ", api_key='"
          + api_key
          + '\''
          + ", name='"
          + name
          + '\''
          + ", type='"
          + type
          + '\''
          + ", distribution='"
          + distribution
          + '\''
          + '}';
    }
  }
}
