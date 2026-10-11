package com.forgeai.project.entity;

// One request to carry a task from repository to pull request.
// The row is the single source of truth for progress: the background
// worker writes each step here and clients poll it.

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
@Getter
@Setter
public class AgentRun {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "task", nullable = false, columnDefinition = "TEXT")
    private String task;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AgentRunStatus status;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "plan_json", columnDefinition = "TEXT")
    private String planJson;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "base_branch", length = 255)
    private String baseBranch;

    @Column(name = "base_sha", length = 64)
    private String baseSha;

    @Column(name = "branch_name", length = 255)
    private String branchName;

    @Column(name = "pull_request_number")
    private Integer pullRequestNumber;

    @Column(name = "pull_request_url", length = 500)
    private String pullRequestUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sandbox_seconds")
    private Double sandboxSeconds;

    @Column(name = "estimated_cost_usd")
    private Double estimatedCostUsd;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
