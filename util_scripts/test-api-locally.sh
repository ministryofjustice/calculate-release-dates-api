#!/bin/bash
#
# Script to test the calculate-release-dates-api calls locally
# 
# Parameters:
#       1. CLIENT - the client Id and secret, clear-text, colon-separated
#          (use the locally-seeded 'calculate-release-dates-admin' client - it has the right roles)
#
# Example:
# 
# $ ./test-api-locally.sh calculate-release-dates-admin:client_secret | tee output.txt
#

CLIENT=${1?No client specified}
USER=CALCULATE_RELEASE_DATES_LOCAL
SERVER_PORT=8089

# Set the environment-specific hostname for the hmpps-auth service
AUTH_HOST="http://localhost:9090"
API_HOST="http://localhost:${SERVER_PORT}"

# Get the token for the client name / secret and store it in the environment variable TOKEN
TOKEN_RESPONSE=$(curl -s -k -d "" -X POST "$AUTH_HOST/auth/oauth/token?grant_type=client_credentials&username=$USER" -H "Authorization: Basic $(echo -n $CLIENT | base64)")
TOKEN=$(echo "$TOKEN_RESPONSE" | jq -er .access_token)
if [[ $? -ne 0 ]]; then
  echo "Failed to read token from credentials response"
  echo "$TOKEN_RESPONSE"
  exit 1
fi

AUTH_TOKEN="Bearer $TOKEN"

echo "---------------------------------------------------------------"
echo $AUTH_TOKEN
echo "---------------------------------------------------------------"
# Call the test data end point
curl -X GET --location "$API_HOST/test/data" -H "Authorization: $AUTH_TOKEN" -H "Accept: application/json" -H "Content-type: application/json" | jq .

# Calculate the release dates for a test user (requires prisonApi running locally)
# curl -X GET --location "$API_HOST/calculation/by-prisoner-id/A1234AA" -H "Authorization: $AUTH_TOKEN" -H "Accept: application/json" -H "Content-type: application/json" | jq .

# End
