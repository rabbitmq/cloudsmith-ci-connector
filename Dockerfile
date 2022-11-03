FROM ubuntu:22.04 as builder

RUN set -eux; \
	apt-get update; \
	apt-get -y upgrade; \
	apt-get install --yes --no-install-recommends \
		ca-certificates \
		wget \
		gnupg \
		jq

ARG JAVA_VERSION="17"

RUN ARCH="x86"; BUNDLE="jdk"; \
    wget "https://api.azul.com/zulu/download/community/v1.0/bundles/latest/?java_version=$JAVA_VERSION&ext=tar.gz&os=linux&arch=$ARCH&hw_bitness=64&release_status=ga&bundle_type=$BUNDLE" -O jdk-info.json

RUN wget --progress=bar:force:noscroll -O "jdk.tar.gz" $(cat jdk-info.json | jq --raw-output .url)
RUN echo "$(cat jdk-info.json | jq --raw-output .sha256_hash) *jdk.tar.gz" | sha256sum --check --strict -

RUN set -eux; \
    JAVA_PATH="/usr/lib/jdk-$JAVA_VERSION"; \
    mkdir $JAVA_PATH && \
    tar --extract  --file jdk.tar.gz --directory "$JAVA_PATH" --strip-components 1; \
	  $JAVA_PATH/bin/jlink --compress=2 --output /jre --add-modules java.base,jdk.crypto.cryptoki,java.net.http; \
	  /jre/bin/java -version \
    ; \
    mkdir -p /app

COPY target/concourse-cloudsmith-resource.jar /app

FROM ubuntu:22.04

RUN set -eux; \
	\
	apt-get update; \
	apt-get -y upgrade; \
	apt-get install --yes --no-install-recommends \
		ca-certificates \
  ; \
	rm -rf /var/lib/apt/lists/*

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk/jre
RUN mkdir -p $JAVA_HOME
COPY --from=builder /jre $JAVA_HOME/
RUN ln -svT $JAVA_HOME/bin/java /usr/local/bin/java

COPY --from=builder /app/* /opt/resource/
RUN set -eux; \
    java -jar /opt/resource/concourse-cloudsmith-resource.jar test

RUN groupadd --gid 1000 concourse
RUN useradd --uid 1000 --gid concourse --comment "concourse user" concourse

USER concourse:concourse

COPY scripts/* /opt/resource/
