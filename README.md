# Calculate Release Dates

[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=for-the-badge&logo=github&label=MoJ%20Compliant&query=%24.data%5B%3F%28%40.name%20%3D%3D%20%22calculate-release-dates%22%29%5D.status&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fgithub_repositories)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/github_repositories#calculate-release-dates "Link to report")

This service provides a calculation engine by which release dates of sentences are calculated. 
It also allows for an existing calculation result to be retrieved.

* The main client is the [Calculate Release Dates UI](https://github.com/ministryofjustice/calculate-release-dates) service.
* It is built as  docker image and deployed to the MOJ Cloud Platform.

# Dependencies
This service requires a postgresql database.

# Building the project
Tools required:
* JDK v16+
* Kotlin
* docker
* docker-compose

## Install gradle
`$ ./gradlew`
`$ ./gradlew clean build`

# Running the service
Start up the docker dependencies using the docker-compose file in the `calculate-release-dates-api` service
There is a script to help, which sets local profiles, port and DB connection properties to the
values required.

# Instructions

If this is a HMPPS project then the project will be created as part of bootstrapping - 
see https://github.com/ministryofjustice/dps-project-bootstrap.

`$ ./run-full.sh`
+Or, to run with default properties set in the docker-compose file

`$ docker-compose pull && docker-compose up`

Or, to use default port and properties

`$ SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun`


# Running the unit tests

Unit tests mock all external dependencies and can be run with no dependent containers.

`$ ./gradlew test`

# Running the integration tests

Integration tests use Wiremock to stub any API calls required, and use a local H2 database
that is seeded with data specific to each test suite.

`$ ./gradlew integrationTest`

# Linting

`$ ./gradlew ktlintcheck`

# OWASP Dependency Checking scanning

`$ ./gradlew dependencyCheckAnalyze`