package com.forgeai.project.exception;

public class RepositoryNotConnectedException extends RuntimeException {

    public RepositoryNotConnectedException(Long projectId) {
        super("Project " + projectId + " is not connected to a GitHub repository");
    }
}
