package com.forgeai.project.dto;

// What Java sends to the Python AI service for planning.
// analysis is optional: when present, Python shows its relevant files first.

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiPlanRequest {

    private RepositorySnapshotContext context;
    private String task;
    private RepositoryAnalysisResponse analysis;
}
