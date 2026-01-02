package com.jamesward.springaimcpdemo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Configuration for AgentCore authentication and URL transformation.
 * - Uses a pre-obtained JWT token passed via environment variable
 * - Transforms URLs to work with AgentCore's invocation endpoint:
 *   - Removes /mcp suffix (Spring AI MCP client appends this)
 *   - URL-encodes the ARN in the path
 */
@Configuration
@ConditionalOnProperty(name = "JWT_TOKEN")
public class AgentCoreAuthConfig {

    @Value("${JWT_TOKEN}")
    private String jwtToken;

    @Bean
    public WebClient.Builder mcpWebClientBuilder() {
        System.out.println("Configuring MCP client with JWT authentication and AgentCore URL transformation");
        return WebClient.builder()
                .filter(transformAgentCoreUrl())
                .filter(addAuthorizationHeader());
    }

    /**
     * Transform URLs for AgentCore compatibility:
     * - Remove /mcp suffix that Spring AI MCP client appends
     * - Properly URL-encode the ARN in the path
     * - Remove Mcp-Session header (server is stateless)
     */
    private ExchangeFilterFunction transformAgentCoreUrl() {
        return (request, next) -> {
            URI originalUri = request.url();
            String path = originalUri.getPath();

            // Only transform bedrock-agentcore URLs
            if (originalUri.getHost() != null && originalUri.getHost().contains("bedrock-agentcore")) {
                // Remove /mcp suffix if present
                if (path.endsWith("/mcp")) {
                    path = path.substring(0, path.length() - 4);
                }

                // URL-encode the ARN in the path (colons need to be encoded)
                // Path format: /runtimes/{arn}/invocations
                if (path.contains("/runtimes/arn:")) {
                    int runtimesStart = path.indexOf("/runtimes/") + 10;
                    int invocationsIdx = path.indexOf("/invocations");
                    if (invocationsIdx > runtimesStart) {
                        String arn = path.substring(runtimesStart, invocationsIdx);
                        String encodedArn = URLEncoder.encode(arn, StandardCharsets.UTF_8);
                        path = "/runtimes/" + encodedArn + path.substring(invocationsIdx);
                    }
                }

                URI transformedUri = UriComponentsBuilder.fromUri(originalUri)
                        .replacePath(path)
                        .build(true)
                        .toUri();

                System.out.println("Transformed URL: " + originalUri + " -> " + transformedUri);

                // Build new request with transformed URL and without Mcp-Session header
                // (server is stateless and doesn't maintain sessions)
                ClientRequest.Builder requestBuilder = ClientRequest.from(request)
                        .url(transformedUri);

                // Copy all headers except Mcp-Session
                request.headers().forEach((name, values) -> {
                    if (!"Mcp-Session".equalsIgnoreCase(name)) {
                        values.forEach(value -> requestBuilder.header(name, value));
                    }
                });

                // Clear original headers and rebuild
                ClientRequest transformedRequest = ClientRequest.from(request)
                        .url(transformedUri)
                        .headers(headers -> headers.remove("Mcp-Session"))
                        .build();

                return next.exchange(transformedRequest);
            }

            return next.exchange(request);
        };
    }

    private ExchangeFilterFunction addAuthorizationHeader() {
        return (request, next) -> {
            ClientRequest authorizedRequest = ClientRequest.from(request)
                    .header("Authorization", "Bearer " + jwtToken)
                    .build();
            return next.exchange(authorizedRequest);
        };
    }
}
