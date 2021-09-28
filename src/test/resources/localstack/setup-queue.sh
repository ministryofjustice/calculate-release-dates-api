#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2

aws --endpoint-url=http://localhost:4566 sns create-topic --name domain_events
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name rp_api_dlq
aws --endpoint-url=http://localhost:4566 sqs create-queue --queue-name rp_api_queue \
    --attributes '{"RedrivePolicy": "{\"deadLetterTargetArn\":\"arn:aws:sqs:eu-west-2:000000000000:rp_api_dlq\",\"maxReceiveCount\":\"5\"}"}'

aws --endpoint-url=http://localhost:4566 sns subscribe \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:domain_events \
    --protocol sqs \
    --notification-endpoint http://localhost:4566/queue/rp_api_queue
