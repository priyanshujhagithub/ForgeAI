package com.forgeai.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubPullRequestResponse {

    private Integer number;

    @JsonProperty("html_url")
    private String htmlUrl;
}
