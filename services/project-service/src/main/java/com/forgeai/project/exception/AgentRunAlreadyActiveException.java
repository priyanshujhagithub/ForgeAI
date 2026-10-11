package com.forgeai.project.exception;

public class AgentRunAlreadyActiveException extends RuntimeException {

    public AgentRunAlreadyActiveException(Long projectId) {
        super("Project " + projectId + " already has a run in progress");
    }
}
