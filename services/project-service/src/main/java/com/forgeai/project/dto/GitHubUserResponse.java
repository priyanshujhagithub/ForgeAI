package com.forgeai.project.dto;

//  This represents GitHub's answer to:
//
//    GET /user

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubUserResponse {

    private Long id;
    private String login;
}
