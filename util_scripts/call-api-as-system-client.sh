#!/bin/bash
#
# NAME:  call-api-as-system-client.sh
# Script to test APIs in the DEV environment or locally which grabs the system token from kubectl, gets an auth token and then
# invokes your URL.
# 
# Parameters:
# 1. URL - the absolute URL you want to call, you may need to quote the URL
#
# Example:
# 
# $ ./call-api-as-system-client.sh <URL>
#

AUTH_HOST="https://sign-in-dev.hmpps.service.justice.gov.uk"

read -r user secret < <(echo $(kubectl -n calculate-release-dates-dev get secret calculate-release-dates -o json | jq '.data[] |= @base64d' | jq -r '.data.SYSTEM_CLIENT_ID, .data.SYSTEM_CLIENT_SECRET'))

BASIC_AUTH="$(echo -n $user:$secret | base64)"
TOKEN_RESPONSE=$(curl -s -k -d "" -X POST "$AUTH_HOST/auth/oauth/token?grant_type=client_credentials" -H "Authorization: Basic $BASIC_AUTH")
TOKEN=$(echo "$TOKEN_RESPONSE" | jq -er .access_token)
if [[ $? -ne 0 ]]; then
  echo "Failed to read token from credentials response"
  echo "$TOKEN_RESPONSE"
  exit 1
fi

curl -s -X GET --location "$1" -H "Authorization: Bearer $TOKEN" -H "Accept: application/json" | jq .

# End
