# Spring AI MCP Elicitation Demo

## Setup
1. [Create a Bedrock Bearer token](https://us-east-1.console.aws.amazon.com/bedrock/home?region=us-east-1#/api-keys/long-term/create)
2. Set the env var: `export AWS_BEARER_TOKEN_BEDROCK=YOUR_TOKEN`

Start the MCP server:
```
./gradlew :server:bootRun
```

Explore in the MCP Inspector `http://localhost:8081/mcp`



In another terminal, set `AWS_BEARER_TOKEN_BEDROCK` again and run the MCP client:
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
