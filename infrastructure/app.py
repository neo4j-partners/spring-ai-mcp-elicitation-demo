#!/usr/bin/env python3
"""CDK App entry point for Spring AI MCP Elicitation Demo."""
import aws_cdk as cdk
from mcp_server_stack import McpServerStack

app = cdk.App()
McpServerStack(app, "SpringAiMcpElicitationDemo")

app.synth()
