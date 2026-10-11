package com.forgeai.project.dto;

// GitHub's answer to GET /repos/{owner}/{repo}/git/blobs/{sha}

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubBlobResponse {

    private String sha;
    private String content;
    private String encoding;
}
