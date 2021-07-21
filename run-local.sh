#
# This script is used to run the Create and Vary a licence API locally, to interact with
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

# Client id/secret to access local container-hosted services
# Matches with the seeded client details in hmpps-auth for its dev profile
export SYSTEM_CLIENT_ID=calculate-release-dates-admin
export SYSTEM_CLIENT_SECRET=client_secret

# Provide the DB connection details to local container-hosted Postgresql DB
# Match with the credentials set in docker-compose.yml
export DB_SERVER=localhost
export DB_NAME=licences
export DB_USER=licences
export DB_PASS=password

# Provide URLs to other local container-based dependent services
# Match with ports defined in docker-compose.yml
export HMPPS_AUTH_URL=http://localhost:9090/auth

# Make the connection without specifying the sslmode=verify-full requirement
export SPRING_DATASOURCE_URL='jdbc:postgresql://${DB_SERVER}/${DB_NAME}'

# Run the application with stdout and dev profiles active
SPRING_PROFILES_ACTIVE=stdout,dev ./gradlew bootRun

# End

