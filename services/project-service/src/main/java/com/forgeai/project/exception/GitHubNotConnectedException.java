package com.forgeai.project.exception;

public class GitHubNotConnectedException extends RuntimeException {

    public GitHubNotConnectedException() {
        super("GitHub account is not connected");
    }
}
