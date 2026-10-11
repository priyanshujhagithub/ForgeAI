package com.forgeai.project.dto;

// What the API returns about an agent run. Fields fill in as the run advances.

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class AgentRunResponse {

    private UUID id;
    private Long projectId;
    private String task;
    private String status;
    private boolean active;

    private RepositoryAnalysisResponse analysis;
    private EngineeringPlanResponse plan;
    private LoopResultResponse result;

    private String baseBranch;
    private String baseSha;
    private String branchName;
    private Integer pullRequestNumber;
    private String pullRequestUrl;

    private String errorMessage;
    private Double sandboxSeconds;
    private Double estimatedCostUsd;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}
