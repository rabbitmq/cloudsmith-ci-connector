FROM ghcr.io/graalvm/native-image:ol8-java17-22.2.0 as builder

COPY target/concourse-cloudsmith-resource.jar .

RUN native-image -jar concourse-cloudsmith-resource.jar \
		-H:Features="com.rabbitmq.concourse.NativeImageFeature" \
		--no-fallback --enable-url-protocols=https

FROM ubuntu:20.04

RUN set -eux; \
	\
	apt-get update; \
	apt-get install --yes --no-install-recommends \
		ca-certificates

COPY --from=builder /app/concourse-cloudsmith-resource /opt/resource/
COPY scripts/* /opt/resource/
