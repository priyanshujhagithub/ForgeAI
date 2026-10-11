package com.forgeai.project.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GitHubRepositorySnapshot {

    private String owner;
    private String repository;
    private List<GitHubFileResponse> files;
}
