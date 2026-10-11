package com.forgeai.project.exception;

import java.util.UUID;

public class AgentRunNotFoundException extends RuntimeException {

    public AgentRunNotFoundException(UUID id) {
        super("Run not found with id: " + id);
    }
}
