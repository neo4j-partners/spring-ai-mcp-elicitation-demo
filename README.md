# Spring AI MCP Elicitation Demo

## Setup

### AWS Credentials

This demo uses AWS Bedrock, which requires valid AWS credentials. You can use either SSO or static credentials.

> **Note:** Spring AI Bedrock does NOT support `AWS_BEARER_TOKEN_BEDROCK` or Bedrock API Keys for authentication. It uses the standard AWS SDK credential chain. See the [Spring AI Bedrock documentation](https://docs.spring.io/spring-ai/reference/api/bedrock.html) for supported authentication methods.

**Option 1: AWS SSO (Recommended)**

1. List your available AWS profiles:
   ```
   cat ~/.aws/config
   ```

2. Find a profile with Bedrock access (look for profiles with `sso_session` or static credentials).

3. Login with SSO (if using an SSO profile):
   ```
   aws sso login --profile YOUR_PROFILE_NAME
   ```

4. Set the profile environment variable:
   ```
   export AWS_PROFILE=YOUR_PROFILE_NAME
   ```

5. Verify credentials are working:
   ```
   aws sts get-caller-identity
   ```

**Option 2: Static Credentials**

Set environment variables directly:
```
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=us-east-1
```

Or configure in `~/.aws/credentials`:
```
[default]
aws_access_key_id = your_access_key
aws_secret_access_key = your_secret_key
region = us-east-1
```

### Running the Demo

Start the MCP server:
```
./gradlew :server:bootRun
```

Explore in the MCP Inspector `http://localhost:8081/mcp`

In another terminal (with the same AWS credentials configured), run the MCP client:
```
./gradlew :client:bootRun
```

In the client's console, ask:
```
search flights denver to san francisco tomorrow
```

```mermaid
sequenceDiagram
  autonumber
  actor U as User
  participant A as AI Agent
  participant MCP as MCP Server
  participant AI as AI Model (Bedrock)

  U->>A: "search flights denver to san francisco tomorrow"
  A->>AI: inference request (user query)
  AI-->>A: call tool "flightSearch"

  rect rgb(240, 240, 240)
    note right of A: Tool call with Elicitation
    A->>MCP: do tool call
    MCP-->>A: elicit preferred airline
    A-->>U: "preferred airline?"
    U->>A: "United"
    A->>MCP: continue with tool call
    MCP-->>A: return flight results
  end
  
  A->>AI: inference request (user query and tool results)
  AI-->>A: flight result summary
  A-->>U: display flight summary
```
