# Overview

This is a Concourse resource to check, get, push, and, delete packages from [Cloudsmith](https://cloudsmith.io/).

# Building

To run tests:

```shell
make test
```

To push the Docker image:

```shell
make push-docker-image
```

# Usage

## Source Configuration

* `username`: *Required.* The username for the Cloudsmith account.
* `organization`: *Required.* The organization name.
* `repository`: *Required.* The repository name.
* `api_key`: *Required.* API key used for all requests. 
* `name`: *Required.* Name of the package(s). Regular expressions, e.g. `^erlang$`, accepted.
* `type`: *Required*. Type of the packages in the repository (`deb`, `rpm`, or `raw`).
* `version`: *Required*. Version of the package(s), e.g. `2.*`.
* `distribution`: *Required*. The distribution, e.g. `ubuntu/focal`.
* `tags`: *Optional*. Tags to filter out packages for `in`.

## Behaviour

### `check`: Check for packages.

### `in`: Fetch packages.

### `out`: Upload packages.

#### Parameters

* `tags`: *Required.* Comma-separated list of tags that will be applied to packages.
* `local_path`: *Optional.* The directory to look into for package files. Default is the current directory.
* `globs`: *Optional.* Comma-separated list of globs for files that will uploaded.

# Examples

```yaml
# declare the resource type
---
resource_types:
  - name: cloudsmith-package
    type: docker-image
    source:
      repository: pivotalrabbitmq/cloudsmith-package-resource
      tag: latest

# declare a repository to check for new versions of Erlang debian packages
resources:
  - name: rabbitmq-erlang
    type: cloudsmith-package
    source:
      username: team-rabbitmq
      organization: rabbitmq
      repository: rabbitmq-erlang
      api_key: the-api-key
      name: '^erlang*'
      type: deb
      distribution: 'ubuntu/focal'
      version: '1:23*'
      
# declare a repository to publish to 
  - name: rabbitmq-erlang
    type: cloudsmith-package
    source:
      username: team-rabbitmq
      organization: rabbitmq
      repository: rabbitmq-erlang
      api_key: the-api-key
      distribution: 'ubuntu/focal'

# in job definition
        - put: rabbitmq-erlang
          params:
            local_path: PACKAGES 
            globs: 'erlang-1:23*.deb'
            tags: 'erlang,erlang-23.x'
```
