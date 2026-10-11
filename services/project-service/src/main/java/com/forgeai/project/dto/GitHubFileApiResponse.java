package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubFileApiResponse {

    private String path;
    private String content;
    private String sha;
    private String encoding;
}
