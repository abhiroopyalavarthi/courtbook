#!/usr/bin/env bash
# Creates the AWS pieces CourtBook needs, except RDS and EC2 (see README.md in
# this folder for those). Safe to re-run: it skips anything that exists.
#
#   export AWS_REGION=us-east-1
#   ./deploy/aws/provision.sh
set -euo pipefail

REGION="${AWS_REGION:?set AWS_REGION}"
PREFIX="courtbook"
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
echo "Account $ACCOUNT, region $REGION"

# --- SQS: the event queue plus a dead-letter queue for messages that fail 5 times
DLQ_URL=$(aws sqs create-queue --queue-name "$PREFIX-events-dlq" \
  --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn \
  --query Attributes.QueueArn --output text)
REDRIVE="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"5\"}"
QUEUE_URL=$(aws sqs create-queue --queue-name "$PREFIX-events" --query QueueUrl --output text)
aws sqs set-queue-attributes --queue-url "$QUEUE_URL" --attributes "{
  \"VisibilityTimeout\": \"30\",
  \"ReceiveMessageWaitTimeSeconds\": \"10\",
  \"RedrivePolicy\": $(printf '%s' "$REDRIVE" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')
}"
QUEUE_ARN=$(aws sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn \
  --query Attributes.QueueArn --output text)
echo "Queue: $QUEUE_URL"

# --- DynamoDB: three on-demand tables
create_table() {
  local name=$1 pk=$2 sk=${3:-}
  if aws dynamodb describe-table --table-name "$name" >/dev/null 2>&1; then
    echo "Table $name exists"
    return
  fi
  local attrs="AttributeName=$pk,AttributeType=S"
  local keys="AttributeName=$pk,KeyType=HASH"
  if [[ -n "$sk" ]]; then
    attrs="$attrs AttributeName=$sk,AttributeType=S"
    keys="$keys AttributeName=$sk,KeyType=RANGE"
  fi
  # shellcheck disable=SC2086
  aws dynamodb create-table --table-name "$name" --billing-mode PAY_PER_REQUEST \
    --attribute-definitions $attrs --key-schema $keys >/dev/null
  aws dynamodb wait table-exists --table-name "$name"
  echo "Created table $name"
}
create_table "$PREFIX-notifications" userId sk
create_table "$PREFIX-feed" feedId sk
create_table "$PREFIX-processed-events" eventId
# Old de-duplication markers expire on their own.
aws dynamodb update-time-to-live --table-name "$PREFIX-processed-events" \
  --time-to-live-specification "Enabled=true,AttributeName=expiresAt" >/dev/null 2>&1 || true

# --- IAM: a role for the EC2 instance that can use only this queue and these tables
ROLE="$PREFIX-ec2"
if ! aws iam get-role --role-name "$ROLE" >/dev/null 2>&1; then
  aws iam create-role --role-name "$ROLE" --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{"Effect": "Allow", "Principal": {"Service": "ec2.amazonaws.com"}, "Action": "sts:AssumeRole"}]
  }' >/dev/null
  echo "Created role $ROLE"
fi
TABLE_ARN_PREFIX="arn:aws:dynamodb:$REGION:$ACCOUNT:table/$PREFIX-"
aws iam put-role-policy --role-name "$ROLE" --policy-name "$PREFIX-app" --policy-document "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [
    {
      \"Effect\": \"Allow\",
      \"Action\": [\"sqs:SendMessage\", \"sqs:ReceiveMessage\", \"sqs:DeleteMessage\",
                   \"sqs:GetQueueUrl\", \"sqs:GetQueueAttributes\", \"sqs:ChangeMessageVisibility\"],
      \"Resource\": \"$QUEUE_ARN\"
    },
    {
      \"Effect\": \"Allow\",
      \"Action\": [\"dynamodb:GetItem\", \"dynamodb:PutItem\", \"dynamodb:Query\",
                   \"dynamodb:UpdateItem\", \"dynamodb:ConditionCheckItem\", \"dynamodb:DescribeTable\"],
      \"Resource\": [\"${TABLE_ARN_PREFIX}notifications\", \"${TABLE_ARN_PREFIX}feed\",
                     \"${TABLE_ARN_PREFIX}processed-events\"]
    }
  ]
}"
if ! aws iam get-instance-profile --instance-profile-name "$ROLE" >/dev/null 2>&1; then
  aws iam create-instance-profile --instance-profile-name "$ROLE" >/dev/null
  aws iam add-role-to-instance-profile --instance-profile-name "$ROLE" --role-name "$ROLE"
  echo "Created instance profile $ROLE"
fi

echo
echo "Done. Attach instance profile '$ROLE' to the EC2 instance."
