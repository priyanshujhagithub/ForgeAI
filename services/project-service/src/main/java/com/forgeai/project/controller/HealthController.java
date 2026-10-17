package com.forgeai.project.controller;

import com.forgeai.project.ai.AiServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Two unauthenticated endpoints for running on a host that puts idle
// services to sleep.
//
// /health answers from this process alone. It touches neither the database
// nor the AI service, so a platform health check never keeps them awake.
//
// /wake is called by the web app as soon as a visitor opens it. It also
// nudges the AI service, so both are starting up while the visitor reads the
// page or types a password, not after they press a button.

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final AiServiceClient aiServiceClient;

    public HealthController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/wake")
    public Map<String, String> wake() {
        aiServiceClient.wakeUp();
        return Map.of("status", "ok");
    }
}
