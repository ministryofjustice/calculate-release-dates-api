# Calculate Release Dates

[![repo standards badge](https://img.shields.io/badge/dynamic/json?color=blue&style=for-the-badge&logo=github&label=MoJ%20Compliant&query=%24.data%5B%3F%28%40.name%20%3D%3D%20%22calculate-release-dates-api%22%29%5D.status&url=https%3A%2F%2Foperations-engineering-reports.cloud-platform.service.justice.gov.uk%2Fgithub_repositories)](https://operations-engineering-reports.cloud-platform.service.justice.gov.uk/github_repositories#calculate-release-dates-api "Link to report")
[![Docker Repository on Quay](https://quay.io/repository/hmpps/calculate-release-dates-api/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/calculate-release-dates-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://calculate-release-dates-api-dev.hmpps.service.justice.gov.uk/swagger-ui.html)

This service provides a calculation engine by which release dates of sentences are calculated.
It also allows for an existing calculation result to be retrieved.

* The main client is the [Calculate Release Dates UI](https://github.com/ministryofjustice/calculate-release-dates) service.
* It is built as  docker image and deployed to the MOJ Cloud Platform.

# Dependencies
This service requires a postgresql database.

# Building the project
Tools required:
* JDK v18+
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
see https://github.com/ministryofjustice/hmpps-project-bootstrap.

`$ ./run-full.sh`
+Or, to run with default properties set in the docker-compose file

`$ docker-compose pull && docker-compose up`

Or, to use default port and properties

`$ SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun`


# Running the unit tests

Unit tests mock all external dependencies and can be run with no dependent containers.

`$ ./gradlew test`

# Linting

`$ ./gradlew ktlintcheck`

# OWASP Dependency Checking scanning

`$ ./gradlew dependencyCheckAnalyze`

# Running the service locally using run-local.sh
**_N.B. This currently still requires Adjustments and Manage Offences to be configured to look at dev environment_**

This will run the service locally. It starts the database runs manage-offences-api via a bash script. It connects to the dev versions of prison-api and hmpps-auth
Run the following commands from the root directory of the project:
1. docker-compose -f docker-compose-test.yml pull
2. docker-compose -f docker-compose-test.yml up --no-start
3. docker-compose -f docker-compose-test.yml start hmpps-auth calculate-release-dates-db prison-api
4. ./run-local.sh

# profiles
[How we use application profile files to configure calculation variables](docs/profile.md)