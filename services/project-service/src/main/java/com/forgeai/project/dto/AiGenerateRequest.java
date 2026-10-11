package com.forgeai.project.dto;

// What Java sends to the Python AI service for code generation.

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiGenerateRequest {

    private RepositorySnapshotContext context;
    private String task;
    private EngineeringPlanResponse plan;
}
