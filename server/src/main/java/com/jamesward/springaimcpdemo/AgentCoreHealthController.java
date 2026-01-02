package com.jamesward.springaimcpdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint required by Amazon Bedrock AgentCore Runtime.
 * Returns status and timestamp following the AgentCore HTTP protocol contract.
 */
@RestController
public class AgentCoreHealthController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "status", "healthy",
            "timeOfLastUpdate", System.currentTimeMillis() / 1000
        );
    }
}
