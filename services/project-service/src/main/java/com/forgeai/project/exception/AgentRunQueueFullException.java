package com.forgeai.project.exception;

public class AgentRunQueueFullException extends RuntimeException {

    public AgentRunQueueFullException() {
        super("Too many runs are waiting; try again when one finishes");
    }
}
