package com.forgeai.project.entity;

// QUEUED -> ANALYZING -> PLANNING -> GENERATING_AND_TESTING -> OPENING_PR -> COMPLETED
//
// Terminal states other than COMPLETED:
//   COMPLETED_UNTESTED   the repository has no tests; a pull request marked
//                        "untested" is open for a human to check by hand
//   TESTS_FAILED         tests still failing after the attempt limit; no pull request
//   SANDBOX_UNAVAILABLE  changes were generated but could not be tested; no pull request
//   FAILED               anything else (GitHub, AI service, infrastructure)

import java.util.List;

public enum AgentRunStatus {
    QUEUED(true),
    ANALYZING(true),
    PLANNING(true),
    GENERATING_AND_TESTING(true),
    OPENING_PR(true),
    COMPLETED(false),
    COMPLETED_UNTESTED(false),
    TESTS_FAILED(false),
    SANDBOX_UNAVAILABLE(false),
    FAILED(false);

    private final boolean active;

    AgentRunStatus(boolean active) {
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    // Must match the partial unique index in V7__create_agent_runs.sql.
    public static List<AgentRunStatus> activeStatuses() {
        return List.of(QUEUED, ANALYZING, PLANNING, GENERATING_AND_TESTING, OPENING_PR);
    }
}
