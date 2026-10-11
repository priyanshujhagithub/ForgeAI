package com.forgeai.project.dto;

// This exists because GitHub's repository response contains:
// "owner": {
//    "login": "octocat"
//  }

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubOwnerResponse {
    private String login;
}
