package com.jamesward.springaimcpdemo;

import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpElicitation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public ApplicationRunner applicationRunner(ChatClient.Builder chatClientBuilder, ToolCallbackProvider callbackProvider) {
        return args -> {
            var chatClient = chatClientBuilder.defaultTools(new DateTimeTools()).defaultToolCallbacks(callbackProvider).build();

            // Automated test request
            var testRequest = "Search for flights on Delta from SFO to JFK on 2025-03-15";
            System.out.println("\n=== Automated MCP Client Test ===");
            System.out.println("Sending request: " + testRequest);
            System.out.println("================================\n");

            try {
                var response = chatClient.prompt()
                        .user(testRequest)
                        .call()
                        .content();

                System.out.println("\n=== Response ===");
                System.out.println(response);
                System.out.println("================\n");
                System.out.println("TEST COMPLETED SUCCESSFULLY");
            } catch (Exception e) {
                System.err.println("\n=== Error ===");
                System.err.println(e.getMessage());
                e.printStackTrace();
                System.err.println("=============\n");
                System.err.println("TEST FAILED");
                System.exit(1);
            }

            System.exit(0);
        };
    }

    @McpElicitation(clients = {"flights"})
    public McpSchema.ElicitResult handleElicitationRequest(McpSchema.ElicitRequest request) {
        System.out.println("\n=== Elicitation Request ===");
        System.out.println("Server has elicited: " + request.message());

        var props = (Map<String, Object>) request.requestedSchema().get("properties");
        var userData = new HashMap<String, Object>();

        // Auto-respond to elicitation with default values
        props.forEach((prop, schema) -> {
            var description = ((Map<String, String>) schema).get("description");
            String autoValue = getAutoElicitationValue(prop);
            System.out.println("  " + description + " -> " + autoValue);
            userData.put(prop, autoValue);
        });

        System.out.println("Auto-responding with: " + userData);
        System.out.println("===========================\n");

        return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, userData);
    }

    private String getAutoElicitationValue(String prop) {
        // Provide sensible defaults for common elicitation fields
        return switch (prop.toLowerCase()) {
            case "preferredairline" -> "United";
            case "airline" -> "Delta";
            case "class", "cabinclass" -> "Economy";
            case "passengers" -> "1";
            default -> "test-value";
        };
    }
}

class DateTimeTools {

    // todo: should user's timezone be a param?
    @Tool(description = "Get the current date and time in the user's timezone")
    String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

}
