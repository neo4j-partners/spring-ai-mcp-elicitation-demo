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
import java.util.Scanner;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    Scanner scanner = new Scanner(System.in);

    @Bean
    public ApplicationRunner applicationRunner(ChatClient.Builder chatClientBuilder, ToolCallbackProvider callbackProvider) {
        return args -> {
            var chatClient = chatClientBuilder.defaultTools(new DateTimeTools()).defaultToolCallbacks(callbackProvider).build();

            while (true) {
                System.out.print("> ");
                var userInput = scanner.nextLine();

                var response = chatClient.prompt()
                        .user(userInput)
                        .call()
                        .content();

                System.out.println("< " + response);
            }
        };
    }

    @McpElicitation(clients = {"flights"})
    public McpSchema.ElicitResult handleElicitationRequest(McpSchema.ElicitRequest request) {
        System.out.println("Server has elicited: " + request.message());

        var props = (Map<String, Object>) request.requestedSchema().get("properties");

        var userData = new HashMap<String, Object>();

        props.forEach( (prop, schema) -> {
            var description = ((Map<String, String>) schema).get("description");
            System.out.print(description + "? ");
            var userInput = scanner.nextLine();
            userData.put(prop, userInput);
        });

        System.out.println("Sending user data back to server: " + userData);

        return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, userData);
    }
}

class DateTimeTools {

    // todo: should user's timezone be a param?
    @Tool(description = "Get the current date and time in the user's timezone")
    String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

}
