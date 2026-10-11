package com.forgeai.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GitHubConnectionResponse {

    private boolean connected;
    private String githubUsername;
}