package com.forgeai.project.dto;

// Mirrors the Python LoopResult.

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LoopResultResponse {

    // "passed", "tests_failed", "sandbox_unavailable" or "infrastructure_error"
    private String outcome;
    private List<CodeChangeResponse> finalChanges;
    private List<AttemptRecordResponse> attempts;
    private double sandboxSeconds;
    private double estimatedCostUsd;
    private String message;
}
