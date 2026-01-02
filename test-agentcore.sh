#!/bin/bash
# Test script for Spring AI MCP Client with AgentCore
# Usage: ./test-agentcore.sh

set -e

# Configuration defaults (can be overridden by environment variables)
AWS_PROFILE="${AWS_PROFILE:-AdministratorAccess-159878781974}"
AWS_REGION="${AWS_REGION:-us-west-2}"

# AgentCore Runtime ARN
RUNTIME_ARN="${RUNTIME_ARN:-arn:aws:bedrock-agentcore:us-west-2:159878781974:runtime/SpringAiMcpElicitationDemo_SpringAiMcpServer-404Tr5HMNu}"

# Cognito configuration
COGNITO_CLIENT_ID="${COGNITO_CLIENT_ID:-7m7d1vff4m4n6j5jfkncq82ub8}"
COGNITO_USERNAME="${COGNITO_USERNAME:-testuser}"
COGNITO_PASSWORD="${COGNITO_PASSWORD:-SimplePass123}"

# Use raw ARN in URL - Spring AI WebClient will handle encoding
FLIGHTS_MCP_URL="https://bedrock-agentcore.${AWS_REGION}.amazonaws.com/runtimes/${RUNTIME_ARN}/invocations?qualifier=DEFAULT"

echo "============================================"
echo "Spring AI MCP Client - AgentCore Test"
echo "============================================"
echo ""
echo "Configuration:"
echo "  AWS_PROFILE: $AWS_PROFILE"
echo "  AWS_REGION: $AWS_REGION"
echo "  COGNITO_CLIENT_ID: $COGNITO_CLIENT_ID"
echo "  COGNITO_USERNAME: $COGNITO_USERNAME"
echo "  MCP URL: $FLIGHTS_MCP_URL"
echo ""

# Get JWT token via AWS CLI
echo "Obtaining JWT token from Cognito..."
AUTH_PARAMS="USERNAME=${COGNITO_USERNAME},PASSWORD=${COGNITO_PASSWORD}"
JWT_TOKEN=$(aws cognito-idp initiate-auth \
  --client-id "$COGNITO_CLIENT_ID" \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters "$AUTH_PARAMS" \
  --region "$AWS_REGION" \
  --query 'AuthenticationResult.AccessToken' \
  --output text)

if [ -z "$JWT_TOKEN" ] || [ "$JWT_TOKEN" == "None" ]; then
  echo "ERROR: Failed to obtain JWT token"
  exit 1
fi

echo "JWT token obtained successfully"
echo "============================================"
echo ""

# Export environment variables
export AWS_PROFILE
export AWS_REGION
export FLIGHTS_MCP_URL
export JWT_TOKEN

# Run the client
./gradlew :client:bootRun --quiet
