package com.forgeai.project.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubContentResponse {

    private String name;
    private String path;
    private String sha;
    private String type;
    private Integer size;
    private String url;
    private String htmlUrl;
    private String downloadUrl;
}
