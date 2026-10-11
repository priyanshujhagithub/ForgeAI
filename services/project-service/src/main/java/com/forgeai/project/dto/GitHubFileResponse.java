package com.forgeai.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GitHubFileResponse {

    private String path;
    private String content;
    private String sha;
}
