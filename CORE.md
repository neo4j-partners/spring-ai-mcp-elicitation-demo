# Deploying to Amazon Bedrock AgentCore

This document describes how to deploy the Spring AI MCP Elicitation Demo to Amazon Bedrock AgentCore.

## Goal

Demonstrate that a Spring AI MCP server can run on AgentCore Runtime, allowing the existing client to connect to a cloud-hosted endpoint instead of localhost.

## What is AgentCore?

Amazon Bedrock AgentCore is a managed platform for deploying AI agents and MCP servers. It provides serverless hosting, automatic scaling, and built-in authentication. The key component for this demo is AgentCore Runtime, which can host MCP servers accessible via HTTP.

For more details, see the [AgentCore Documentation](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/what-is-bedrock-agentcore.html).

## Prerequisites

- AWS account with AgentCore access (us-west-2 region)
- Docker installed locally
- AWS CLI configured
- Python 3.10+ with uv (recommended) or pip
- Node.js (for CDK)

## Implementation Plan

### Part 1: Deploy the Server

**Step 1: Set up the CDK environment**

```bash
cd infrastructure
uv venv
source .venv/bin/activate
uv pip install -e .
npm install -g aws-cdk  # if not already installed
```

**Step 2: Bootstrap CDK (first time only)**

```bash
cdk bootstrap aws://ACCOUNT_ID/us-west-2
```

**Step 3: Deploy the stack**

```bash
cdk deploy
```

This will:
- Build the Docker image locally (ARM64)
- Push it to ECR
- Create a Cognito User Pool with a test user
- Deploy the MCP server to AgentCore Runtime

**Step 4: Note the outputs**

After deployment, note these values from the stack outputs:
- `MCPServerRuntimeArn` - The ARN of the deployed runtime
- `CognitoUserPoolId` - The Cognito User Pool ID
- `CognitoClientId` - The Cognito client ID for authentication
- `TestUsername` - testuser

**Step 5: Set the test user password**

The CDK deployment creates a test user, but you may need to set the password manually:

```bash
aws cognito-idp admin-set-user-password \
  --user-pool-id <CognitoUserPoolId> \
  --username testuser \
  --password 'Password123' \
  --permanent \
  --region us-west-2
```

**Step 6: Get an authentication token**

```bash
python get_token.py <CognitoClientId> testuser 'Password123' us-west-2
```

**Step 7: Test the deployed server**

Test MCP protocol initialization:

```bash
# URL-encode the runtime ARN
RUNTIME_ARN="<MCPServerRuntimeArn>"
ENCODED_ARN=$(echo "$RUNTIME_ARN" | python3 -c "import sys,urllib.parse; print(urllib.parse.quote(sys.stdin.read().strip(), safe=''))")

# Test MCP initialize
curl -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Accept: application/json, text/event-stream" \
  "https://bedrock-agentcore.us-west-2.amazonaws.com/runtimes/${ENCODED_ARN}/invocations?qualifier=DEFAULT" \
  -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0.0"},"capabilities":{}}}'
```

Expected response shows the MCP server is running:
```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{"listChanged":true}},"serverInfo":{"name":"mcp-server","version":"1.0.0"}}}
```

### Part 2: Update and Test the Client

**Step 1: Update the client configuration**

Update `client/src/main/resources/application.properties` to point to the AgentCore endpoint:

```properties
# Replace localhost URL with AgentCore URL
spring.ai.mcp.client.transport=streamable-http
spring.ai.mcp.client.streamable-http.url=https://bedrock-agentcore.us-west-2.amazonaws.com/runtimes/ENCODED_ARN/invocations?qualifier=DEFAULT
```

**Step 2: Add OAuth token handling**

The client needs to obtain a JWT token and include it in requests. This requires updating the client to use the Cognito authentication flow.

**Step 3: Test end-to-end**

Run the client and verify:
- It connects to the AgentCore-hosted server
- Tool discovery works
- The elicitation flow completes successfully
- Flight search results are returned

## Cleanup

To remove all deployed resources:

```bash
cd infrastructure
cdk destroy
```

## References

- [AgentCore Samples Repository](https://github.com/awslabs/amazon-bedrock-agentcore-samples)
- [Deploying MCP Servers to AgentCore](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/runtime-mcp.html)
- [Spring AI with AgentCore Example](https://dev.to/aws-heroes/amazon-bedrock-agentcore-runtime-part-5-using-custom-agent-with-spring-ai-4n71)
- [Spring AI MCP Server Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html)
