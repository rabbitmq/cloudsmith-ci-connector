SHELL := bash# we want bash behaviour in all shell invocations

.DEFAULT_GOAL = package

### VARIABLES ###
#

### TARGETS ###
#

.PHONY: package-concourse
package-concourse: clean ## Build the binary distribution
	./mvnw package -Dmaven.test.skip -P concourse

.PHONY: docker-image-concourse
docker-image-concourse: package-concourse ## Build Docker image
	@docker build --tag pivotalrabbitmq/concourse-cloudsmith-resource:latest --file Dockerfile-concourse .

.PHONY: push-docker-image-concourse
push-docker-image-concourse: docker-image-concourse ## Push Docker image
	@docker push pivotalrabbitmq/concourse-cloudsmith-resource:latest

.PHONY: package-github
package-github: clean ## Build the binary distribution
	./mvnw package -Dmaven.test.skip -P github

.PHONY: docker-image-github
docker-image-github: package-github ## Build Docker image
	@docker build --tag pivotalrabbitmq/cloudsmith-action:latest --file Dockerfile-github .

.PHONY: push-docker-image-github
push-docker-image-github: docker-image-github ## Push Docker image
	@docker push pivotalrabbitmq/cloudsmith-action:latest

.PHONY: clean
clean: 	## Clean all build artefacts
	./mvnw clean

.PHONY: compile
compile: ## Compile the source code
	./mvnw compile

.PHONY: test
test: ## Run tests
	./mvnw test