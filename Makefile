SHELL := bash# we want bash behaviour in all shell invocations

.DEFAULT_GOAL = package

### VARIABLES ###
#

### TARGETS ###
#

.PHONY: package
package: clean ## Build the binary distribution
	./mvnw package -Dmaven.test.skip

.PHONY: native-image
native-image: package ## Build the native image
	native-image -jar target/concourse-cloudsmith-resource.jar \
		-H:Features="com.rabbitmq.concourse.NativeImageFeature" \
		--no-fallback --enable-url-protocols=https

.PHONY: docker-image
docker-image: package ## Build Docker image
	@docker build --tag pivotalrabbitmq/concourse-cloudsmith-resource:latest .

.PHONY: push-docker-image
push-docker-image: docker-image ## Push Docker image
	@docker push pivotalrabbitmq/concourse-cloudsmith-resource:latest

.PHONY: clean
clean: 	## Clean all build artefacts
	./mvnw clean

.PHONY: compile
compile: ## Compile the source code
	./mvnw compile

.PHONY: test
test: ## Run tests
	./mvnw test