#!/bin/bash
#
# NAME:  dev-api.sh
# Script to test APIs in the DEV environment
# 
# Parameters:
# 1. CLIENT - the client id and secret, colon-separated with any special shell characters escaped.
#           - This is for when there are no special characters in the secret.
#
# Example:
# 
# $ ./dev-api.sh <client>:<secret> 
#

CLIENT=${1?No client specified}
AUTH_HOST="https://sign-in-dev.hmpps.service.justice.gov.uk"
API_HOST="https://api-dev.prison.service.justice.gov.uk"

TOKEN_RESPONSE=$(curl -s -k -d "" -X POST "$AUTH_HOST/auth/oauth/token?grant_type=client_credentials" -H "Authorization: Basic $(echo -n $CLIENT | base64)")
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

curl -X GET --location "$API_HOST/api/offenders/A5170DY" -H "Authorization: $AUTH_TOKEN" -H "Accept: application/json" | jq .

# End
