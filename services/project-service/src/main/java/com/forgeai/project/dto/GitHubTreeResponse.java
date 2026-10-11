package com.forgeai.project.dto;

// GitHub's answer to GET /repos/{owner}/{repo}/git/trees/{sha}?recursive=1

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GitHubTreeResponse {

    private String sha;
    private List<GitHubTreeEntry> tree;
    // true when the repository is too large for one response
    private boolean truncated;
}
