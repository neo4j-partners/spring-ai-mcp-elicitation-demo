# Spring AI MCP Elicitation Demo - Status Report

## Current Status: In Progress

The project demonstrates deploying a Spring AI MCP server to Amazon Bedrock AgentCore and connecting to it with a Spring AI MCP client.

---

## Issues Solved

### 1. MCP Server Deployment to AgentCore
- Successfully deployed Spring AI MCP server to AgentCore
- Server responds to `tools/list` and returns 2 tools (searchFlights, getAirportInfo)
- Docker image builds and deploys correctly via CDK

### 2. URL Encoding Issues
- **Problem**: AgentCore requires URL-encoded ARN in the path (`arn%3Aaws%3A...` instead of `arn:aws:...`)
- **Solution**: Created `AgentCoreAuthConfig.java` with `ExchangeFilterFunction` that:
  - URL-encodes the ARN in the request path
  - Removes `/mcp` suffix that Spring AI MCP client appends
  - Adds JWT Authorization header

### 3. JWT Authentication
- **Problem**: Cognito authentication via Java SDK failed
- **Solution**: Get JWT token via AWS CLI in bash script, pass to client as environment variable

### 4. Automated Test Client
- Created fully automated test client (no user input required)
- Created `test-agentcore.sh` script with all defaults configured
- Client auto-responds to elicitation requests with sensible defaults

---

## Current Issue: Session Management Mismatch

### Problem
The Spring AI MCP client's `streamable-http` transport expects session-based communication, but AgentCore operates statelessly.

**Symptoms**:
```
Server response with Protocol: 2025-06-18, Capabilities: ServerCapabilities[...]  <- Works!
Session xyz was not found on the MCP server  <- Fails on subsequent request
McpTransportSessionNotFoundException: Session xyz not found on the server
```

### Root Cause
1. Spring AI MCP client creates a session ID and sends `Mcp-Session` header
2. AgentCore doesn't maintain session state between requests
3. Server (in STATELESS mode) doesn't recognize the session
4. Client fails when trying to reuse the session

### What We've Tried
1. **STATELESS server mode**: Server works but client expects sessions
2. **STREAMABLE server mode**: Need to redeploy to test if AgentCore forwards session headers
3. **Removing Mcp-Session header via filter**: The session ID is managed internally in the transport, not in HTTP headers we can intercept

---

## Next Steps to Try

### Option 1: Test STREAMABLE Mode (In Progress)
- Changed server to `spring.ai.mcp.server.protocol=STREAMABLE`
- Need to force CDK to rebuild Docker image (currently caching)
- Test if AgentCore properly forwards session headers

### Option 2: Create Custom Transport
- Implement a custom MCP transport that works with AgentCore's stateless invocation model
- Would send independent requests without session management

### Option 3: Use Different Client Architecture
- Instead of using Spring AI MCP client, create a custom HTTP client
- Make direct JSON-RPC calls to AgentCore (proven to work via curl)

---

## Files Modified

| File | Purpose |
|------|---------|
| `server/src/main/resources/application.properties` | MCP server config (STATELESS/STREAMABLE mode) |
| `server/src/main/java/.../Application.java` | Removed elicitation, added preferredAirline param |
| `client/src/main/java/.../Application.java` | Automated test client |
| `client/src/main/java/.../AgentCoreAuthConfig.java` | JWT auth + URL transformation |
| `test-agentcore.sh` | Test script with defaults |

---

## Configuration

```properties
# Server (AgentCore)
RUNTIME_ARN=arn:aws:bedrock-agentcore:us-west-2:159878781974:runtime/SpringAiMcpElicitationDemo_SpringAiMcpServer-404Tr5HMNu
COGNITO_CLIENT_ID=7m7d1vff4m4n6j5jfkncq82ub8
COGNITO_USERNAME=testuser
COGNITO_PASSWORD=SimplePass123
```

---

## Verified Working

- `tools/list` via curl returns 2 tools
- `tools/call` via curl executes successfully
- JWT authentication works
- URL transformation in client works (logs show correct encoding)
- Initial MCP handshake succeeds (server returns capabilities)

## Not Yet Working

- Full end-to-end Spring AI MCP client to AgentCore communication
- Session management between client and stateless AgentCore

---

*Last Updated: 2026-01-01*
