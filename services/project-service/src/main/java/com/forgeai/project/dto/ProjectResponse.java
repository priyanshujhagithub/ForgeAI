package com.forgeai.project.dto;
// This DTO represents:
//
//What the API is allowed to return about a project.
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProjectResponse {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String githubRepoUrl;
    private String githubRepoName;
    private String githubOwner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
