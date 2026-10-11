package com.forgeai.project.dto;

// Everything an agent run needs to know about the repository, read at one
// exact commit so analysis, generation, testing and the pull request all
// agree on the same starting point.
//
// cloneToken is null for public repositories: the GitHub token only leaves
// this service when a private clone actually needs it.

public record RepositoryWorkspace(
        RepositorySnapshotContext context,
        String cloneUrl,
        String baseBranch,
        String baseSha,
        String cloneToken
) {}
