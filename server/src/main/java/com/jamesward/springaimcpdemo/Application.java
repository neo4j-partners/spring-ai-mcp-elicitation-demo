package com.jamesward.springaimcpdemo;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}


record UserProfile(
        @JsonPropertyDescription("Preferred Airline")
        String preferredAirline
) { }

record FlightSegment(String departureAirportCode, LocalDateTime departureDateTime,
                     String arrivalAirportCode, LocalDateTime arrivalDateTime, String airline) { }

record FlightSearchResult(List<FlightSegment> flightSegments) {
    // total duration
}

@Component
class MyChatClient {

    // note: try bean for hot-reload
    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
    }

}

@Service
class FlightSearchService {

    final ChatClient chatClient;

    public FlightSearchService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public List<FlightSearchResult> searchFlights(String departureAirportCode,
                                                  String arrivalAirportCode,
                                                  LocalDateTime dateTime,
                                                  String preferredAirline) {
        var preferredAirlinePrompt = preferredAirline == null || preferredAirline.isBlank() ? "" : "The airline should be " + preferredAirline + ".";
        var prompt = "create 10 example flights between " + departureAirportCode + " and " + arrivalAirportCode + " on " + dateTime + ". Some of them should have connections with a minimum duration of 1hr. " + preferredAirlinePrompt;

        // todo: maybe ListOutputConverter ?

        return chatClient.prompt()
                .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .user(prompt)
                .call()
                .entity(new ParameterizedTypeReference<>() { });
    }

}

@Service
class UserService {

    private UserProfile userProfile = new UserProfile(null);

    UserProfile getUserProfile() {
        return userProfile;
    }

    UserProfile setPreferredAirline(String airline) {
        userProfile = new UserProfile(airline);
        return userProfile;
    }
}

@Component
class MyTools {

    private final FlightSearchService flightSearchService;
    private final UserService userService;

    public MyTools(FlightSearchService flightSearchService, UserService userService) {
        this.flightSearchService = flightSearchService;
        this.userService = userService;
    }

    // todo: validate date is in the future
    @McpTool(description = "search for one-way flights")
    public List<FlightSearchResult> searchFlights(
            @McpArg(description = "departure airport code", required = true)
            String departureAirportCode,

            @McpArg(description = "arrival airport code", required = true)
            String arrivalAirportCode,

            @McpArg(description = "date to search flights for (YYYY-MM-DD)", required = true)
            LocalDate date,

            McpSyncRequestContext context) {

        // in real apps, propagate user identity
        var userProfile = userService.getUserProfile();

        if (userProfile.preferredAirline() == null || userProfile.preferredAirline().isBlank()) {
            System.out.println("No preferred airline set, eliciting one...");
            // todo: elicit a string? Or something with descriptions?
            var elicitResult = context.elicit(UserProfile.class);
            if (elicitResult.action() == McpSchema.ElicitResult.Action.ACCEPT) {
                System.out.println("Elicitation result: " + elicitResult.structuredContent());
                userProfile = userService.setPreferredAirline(elicitResult.structuredContent().preferredAirline());
            }
        }

        // there should be a time-of-day parameter (morning, afternoon, evening) but simplicity we just set the time to noon
        return flightSearchService.searchFlights(departureAirportCode, arrivalAirportCode, date.atTime(12, 0), userProfile.preferredAirline());
    }
}
