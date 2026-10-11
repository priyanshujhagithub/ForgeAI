package com.forgeai.project.dto;

// What Java sends to the Python AI service to generate, test and self-correct.

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiSolveRequest {

    private RepositorySnapshotContext context;
    private String task;
    private AiRepositoryRef repository;
    private EngineeringPlanResponse plan;
    private RepositoryAnalysisResponse analysis;
}
