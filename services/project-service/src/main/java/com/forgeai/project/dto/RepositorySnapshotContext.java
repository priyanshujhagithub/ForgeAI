package com.forgeai.project.dto;

// GitHubRepositorySnapshot
//        =
//GitHub-facing representation
//
//RepositorySnapshotContext
//        =
//AI-facing representation

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class RepositorySnapshotContext {

    private String owner;
    private String repository;
    private List<GitHubFileResponse> files;
    private RepositoryMetadata metadata;
}