# Overview

[![Build (Linux)](https://github.com/rabbitmq/cloudsmith-ci-connector/actions/workflows/test-linux.yml/badge.svg)](https://github.com/rabbitmq/cloudsmith-ci-connector/actions/workflows/test-linux.yml)

This is repository provides the logic for
* a GitHub action to download, upload, and delete packages from [Cloudsmith](https://cloudsmith.io/)
* a [Concourse resource](https://concourse-ci.org/resources.html) to check, get, push, and, delete packages from [Cloudsmith](https://cloudsmith.io/).

# Building

To run tests:

```shell
make test
```

To push the GitHub Actions Docker image:

```shell
make push-docker-image-github
```

To push the Concourse Docker image:

```shell
make push-docker-image-concourse
```

# GitHub Action Usage

## Configuration

* `username`: *Required.* The username for the Cloudsmith account.
* `organization`: *Required.* The organization name.
* `repository`: *Required.* The repository name.
* `api_key`: *Required.* API key used for all requests. 
* `name`: *Optional.*
For download and deletion.
Name of the package files.
Regular expressions, e.g. `^erlang$`, accepted.
* `type`: *Optional*. Type of the packages in the repository (`deb`, `rpm`, or `raw`).
* `distribution`: *Optional*. The distribution, e.g. `ubuntu/focal`.
* `action`: *Required.* The action to perform (`download`, `upload`, or `delete`).
* `globs`: *Optional.* Comma-separated list of globs for files that will be uploaded/downloaded.
* `tags`: *Required.*
For upload.
Comma-separated list of tags that will be applied to packages.
* `local_path`: *Optional.*
The directory to look upload packages from / download packages to.
Default is the current directory.
* `republish`: *Optional*.
For upload.
Flag to override already existing packages.
Default is false.
* `version`: *Optional*.
For upload.
Java regular expression to extract package from file(s) when using "raw" packages.
* `do_delete`: *Optional*.
For deletion.
Actually delete matching packages when using the "delete" mode (temporary flag to avoid deleting packages by mistake).
* `version_filter`: *Optional*.
For download and deletion.
Filter to select the packages.
* `keep_last_n`: *Optional*.
For deletion.
Number of versions to keep when deleting.
Default is 0.
* `keep_last_minor_patches`: *Optional*.
For deletion.
Do not delete last patch versions of identified minors.
Default is false.
* `order_by`: *Optional. One of [version, time]*.
For deletion.
Whether to sort packages by version (the default) or by time.


## Examples

### Publish Packages

```yaml
- name: Publish packages to Cloudsmith
  uses: docker://rabbitmqdevenv/cloudsmith-action:latest
  with:
    username: ${{ secrets.CLOUDSMITH_USERNAME }}
    organization: ${{ secrets.CLOUDSMITH_ORGANIZATION }}
    repository: rabbitmq-erlang
    api_key: ${{ secrets.CLOUDSMITH_API_KEY }}
    distribution: ubuntu/jammy
    action: upload
    republish: true
    local_path: packages
    globs: '*.deb'
    tags: erlang,erlang-26.x
```

### Delete Packages

```yaml
- name: Delete latest versions
  uses: docker://rabbitmqdevenv/cloudsmith-action:latest
  with:
    username: ${{ secrets.CLOUDSMITH_USERNAME }}
    organization: ${{ secrets.CLOUDSMITH_ORGANIZATION }}
    repository: rabbitmq-erlang
    api_key: ${{ secrets.CLOUDSMITH_API_KEY }}
    action: delete
    do_delete: true
    version_filter: 1:25*
    keep_last_n: 2
    keep_last_minor_patches: true
```

# Concourse Resource Usage

## Source Configuration

* `username`: *Required.* The username for the Cloudsmith account.
* `organization`: *Required.* The organization name.
* `repository`: *Required.* The repository name.
* `api_key`: *Required.* API key used for all requests. 
* `name`: *Optional.* Name of the package(s). Regular expressions, e.g. `^erlang$`, accepted. Used to select packages for deletion.
* `type`: *Optional*. Type of the packages in the repository (`deb`, `rpm`, or `raw`).
* `distribution`: *Optional*. The distribution, e.g. `ubuntu/focal`.
* `order_by`: *Optional. One of [version, time]*. For deletion (`out`). Whether to sort packages by version (the default) or by time.

## Behaviour

### `check`: Check for packages.

Check for a new version of packages.

### `in`: Fetch packages.

Get packages.

#### Parameters

* `globs`: *Optional.* Comma-separated list of globs for files that will be downloaded.

### `out`: Upload packages.

Upload or delete packages.

#### Parameters

* `tags`: *Required.* Comma-separated list of tags that will be applied to packages.
* `local_path`: *Optional.* The directory to look into for package files. Default is the current directory.
* `globs`: *Optional.* Comma-separated list of globs for files that will be uploaded.
* `republish`: *Optional*. Flag to override already existing packages. Default is false.
* `version`: *Optional*. Java regular expression to extract package from file(s) when using "raw"
  packages.
* `delete`: *Optional*. Flag to enable the "delete" mode. Default is false.
* `do_delete`: *Optional*. Actually delete matching packages when using the "delete" mode
  (temporary flag to avoid deleting packages by mistake).
* `version_filter`: *Optional*. Filter to select the packages to delete.
* `keep_last_n`: *Optional*. Number of versions to keep when deleting. Default is 0.
* `keep_last_minor_patches`: *Optional*. Do not delete last patch versions of identified minors. Default is false.


## Examples

### Declare the resource type

```yaml
# declare the resource type
---
resource_types:
  - name: cloudsmith-package
    type: docker-image
    source:
      repository: rabbitmqdevenv/concourse-cloudsmith-resource
      tag: latest
```

### Publish `deb` or `rpm` packages

```yaml
# resource declaration
resources

  - name: erlang-23.x-debian-stretch-amd64-cloudsmith-package
    tags: [erlang-elixir]
    type: cloudsmith-package
    source:
      username: team-rabbitmq
      organization: rabbitmq
      repository: rabbitmq-erlang
      api_key: api-key
      distribution: 'debian/stretch'

# in job definition
  - put: erlang-23.x-debian-stretch-amd64-cloudsmith-package
  params:
    local_path: PACKAGES
    globs: '*.deb'
    tags: 'erlang,erlang-23.x'
```

### Publish `raw` packages

```yaml
resources:

  - name: rabbitmq-server-dev-cloudsmith
    type: cloudsmith-package
      source:
        username: team-rabbitmq
        organization: rabbitmq
        repository: rabbitmq-erlang
        api_key: api-key
  
  
# in job definition
  - put: rabbitmq-server-dev-cloudsmith
    params:
      local_path: PACKAGES
      version: 'rabbitmq-server-generic-unix-(\d.*)\.tar\.xz'
      globs: '*rabbitmq-server-generic-unix-*.tar.xz'
      tags: 'stream'
```

### Delete `deb`/`rpm` packages

```yaml
# resource declaration
resources

  - name: erlang-23.x-debian-stretch-amd64-cloudsmith-package
    tags: [erlang-elixir]
    type: cloudsmith-package
    source:
      username: team-rabbitmq
      organization: rabbitmq
      repository: rabbitmq-erlang
      api_key: api-key
      name: '^erlang*'
      distribution: 'debian/stretch'

# in job definition
  - put: erlang-23.x-debian-stretch-amd64-cloudsmith-package
    params:
      delete: true
      do_delete: true
      version_filter: '1:23*'
      keep_last_n: 2
```

### Delete `raw` packages

```yaml
resources:

  - name: rabbitmq-server-dev-cloudsmith
    type: cloudsmith-package
      source:
        username: team-rabbitmq
        organization: rabbitmq
        repository: rabbitmq-erlang
        api_key: api-key
        name: '^rabbitmq-server*' 
  
# in job definition
  - put: rabbitmq-server-dev-cloudsmith
    params:
      delete: true
      do_delete: true
      version_filter: '3.9.*-alpha-stream.*'
      keep_last_n: 2
```

### Download packages

```yaml
# resource declaration
  resources

- name: erlang-23.x-debian-stretch-amd64-cloudsmith-package
  tags: [erlang-elixir]
  type: cloudsmith-package
  check_every: 10m
  source:
    username: team-rabbitmq
    organization: rabbitmq
    repository: rabbitmq-erlang
    api_key: api-key
    name: '^erlang*'
    distribution: 'debian/stretch'

# in job definition
    - get: erlang-23.x-debian-stretch-package-sources
      tags: [erlang-elixir]
      trigger: true
      params:
        globs: '*rabbitmq-server-generic-unix-*.tar.xz'
```

# License and Copyright

(c) 2021-2023 Broadcom. All Rights Reserved.
The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.

This package, the Concourse Cloudsmith Resource, is licensed
under the Mozilla Public License 2.0 ("MPL").

See [LICENSE](./LICENSE).
