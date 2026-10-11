package com.forgeai.project.dto;

// This DTO represents information returned by GitHub about a repository.
// It is simply a temporary representation of data received from GitHub.
// //  This represents GitHub's answer to:
/// /
/// /    GET /repos
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubRepositoryResponse {
    private String name;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("html_url")
    private String htmlUrl;

    private GitHubOwnerResponse owner;

    @JsonProperty("private")
    private boolean privateRepository;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("clone_url")
    private String cloneUrl;
}
