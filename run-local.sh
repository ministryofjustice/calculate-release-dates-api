#
# This script is used to run the Calculate Release Dates API locally, to interact with
# existing Postgresql, localstack, prison-api and hmpps-auth containers.
#
# It runs with a combination of properties from the default spring profile (in application.yaml) and supplemented
# with the -dev profile (from application-dev.yml). The latter overrides some of the defaults.
#
# The environment variables here will also override values supplied in spring profile properties, specifically
# around removing the SSL connection to the database and setting the DB properties, SERVER_PORT and client credentials
# to match those used in the docker-compose files.
#

# Server port - avoid clash with prison-api
export SERVER_PORT=8089

# Provide the DB connection details to local container-hosted Postgresql DB
# Match with the credentials set in docker-compose.yml
export DB_SERVER=localhost:5432
export DB_NAME=calculate_release_dates
export DB_USER=calculate-release-dates
export DB_PASS=calculate-release-dates

# Provide URLs to other dependent services. Dev services used here (can be local if you set uo the dependent services locally)
 export HMPPS_AUTH_URL=https://sign-in-dev.hmpps.service.justice.gov.uk/auth
#export HMPPS_AUTH_URL=http://localhost:9090/auth

# Make the connection without specifying the sslmode=verify-full requirement
export SPRING_DATASOURCE_URL='jdbc:postgresql://${DB_SERVER}/${DB_NAME}'

export DTO_FEATURE_TOGGLE=true

# Run the application with stdout and dev profiles active
SPRING_PROFILES_ACTIVE=stdout,dev,localstack ./gradlew bootRun

# End
