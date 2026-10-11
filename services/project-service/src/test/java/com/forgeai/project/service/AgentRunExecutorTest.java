package com.forgeai.project.service;

import com.forgeai.project.ai.AiServiceClient;
import com.forgeai.project.dto.AiPlanRequest;
import com.forgeai.project.dto.AiSolveRequest;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.dto.RepositoryWorkspace;
import com.forgeai.project.dto.SolveResponse;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.exception.AiServiceException;
import com.forgeai.project.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunExecutorTest {

    private static final UUID RUN_ID = UUID.randomUUID();

    private AgentRunRepository repository;
    private GitHubService gitHubService;
    private AiServiceClient aiServiceClient;
    private PullRequestService pullRequestService;
    private AgentRunExecutor executor;

    private AgentRun run;
    // Every status the run passed through, in order.
    private final List<AgentRunStatus> statusHistory = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(AgentRunRepository.class);
        gitHubService = mock(GitHubService.class);
        aiServiceClient = mock(AiServiceClient.class);
        pullRequestService = mock(PullRequestService.class);

        executor = new AgentRunExecutor(
                repository,
                gitHubService,
                aiServiceClient,
                pullRequestService,
                new ObjectMapper()
        );

        run = new AgentRun();
        run.setId(RUN_ID);
        run.setProjectId(5L);
        run.setUserId(2L);
        run.setTask("Add input validation");
        run.setStatus(AgentRunStatus.QUEUED);

        // The mock repository behaves like a one-row table.
        when(repository.findById(RUN_ID)).thenAnswer(call -> Optional.of(run));
        when(repository.save(any(AgentRun.class))).thenAnswer(call -> {
            AgentRun saved = call.getArgument(0);
            if (statusHistory.isEmpty()
                    || statusHistory.get(statusHistory.size() - 1) != saved.getStatus()) {
                statusHistory.add(saved.getStatus());
            }
            return saved;
        });

        when(gitHubService.prepareWorkspace(2L, 5L)).thenReturn(
                new RepositoryWorkspace(
                        new RepositorySnapshotContext(
                                "octocat",
                                "demo",
                                List.of(),
                                new RepositoryMetadata("PYTHON", false)
                        ),
                        "https://github.com/octocat/demo.git",
                        "main",
                        "base-sha",
                        null
                )
        );
        when(aiServiceClient.analyzeRepository(any()))
                .thenReturn(new RepositoryAnalysisResponse());
        when(aiServiceClient.createPlan(any()))
                .thenReturn(new EngineeringPlanResponse());
    }

    @Test
    void passingRunWalksEveryStepAndOpensAPullRequest() {
        givenSolveOutcome("passed", null);
        when(pullRequestService.open(any(), any(), any())).thenReturn(
                new PullRequestService.OpenedPullRequest(
                        "forgeai/task-1234abcd",
                        7,
                        "https://github.com/octocat/demo/pull/7"
                )
        );

        executor.execute(RUN_ID);

        assertThat(statusHistory).containsExactly(
                AgentRunStatus.ANALYZING,
                AgentRunStatus.PLANNING,
                AgentRunStatus.GENERATING_AND_TESTING,
                AgentRunStatus.OPENING_PR,
                AgentRunStatus.COMPLETED
        );
        assertThat(run.getBaseBranch()).isEqualTo("main");
        assertThat(run.getBaseSha()).isEqualTo("base-sha");
        assertThat(run.getAnalysisJson()).isNotBlank();
        assertThat(run.getPlanJson()).isNotBlank();
        assertThat(run.getResultJson()).contains("\"outcome\":\"passed\"");
        assertThat(run.getSandboxSeconds()).isEqualTo(13.5);
        assertThat(run.getEstimatedCostUsd()).isEqualTo(0.0004);
        assertThat(run.getPullRequestUrl())
                .isEqualTo("https://github.com/octocat/demo/pull/7");
        assertThat(run.getBranchName()).isEqualTo("forgeai/task-1234abcd");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void theSameSnapshotAndCommitAreUsedForEveryStep() {
        givenSolveOutcome("passed", null);
        when(pullRequestService.open(any(), any(), any())).thenReturn(
                new PullRequestService.OpenedPullRequest("b", 1, "u")
        );

        executor.execute(RUN_ID);

        ArgumentCaptor<AiPlanRequest> planRequest =
                ArgumentCaptor.forClass(AiPlanRequest.class);
        ArgumentCaptor<AiSolveRequest> solveRequest =
                ArgumentCaptor.forClass(AiSolveRequest.class);
        verify(aiServiceClient).createPlan(planRequest.capture());
        verify(aiServiceClient).solve(solveRequest.capture());

        // The repository is read from GitHub exactly once.
        verify(gitHubService).prepareWorkspace(2L, 5L);
        assertThat(solveRequest.getValue().getContext())
                .isSameAs(planRequest.getValue().getContext());
        assertThat(solveRequest.getValue().getRepository().getBaseSha())
                .isEqualTo("base-sha");
        assertThat(solveRequest.getValue().getRepository().getCloneUrl())
                .isEqualTo("https://github.com/octocat/demo.git");
        // Public repository: no GitHub token is handed to the AI service.
        assertThat(solveRequest.getValue().getRepository().getAccessToken()).isNull();
        // The plan made in step 2 is the plan that gets implemented.
        assertThat(solveRequest.getValue().getPlan()).isNotNull();
    }

    @Test
    void repositoryWithoutTestsGetsAPullRequestMarkedUntested() {
        givenSolveOutcome(
                "no_tests",
                "This repository has no tests, so the change could not be verified."
        );
        when(pullRequestService.open(any(), any(), any())).thenReturn(
                new PullRequestService.OpenedPullRequest(
                        "forgeai/task-1234abcd",
                        8,
                        "https://github.com/octocat/demo/pull/8"
                )
        );

        executor.execute(RUN_ID);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED_UNTESTED);
        assertThat(run.getStatus().isActive()).isFalse();
        assertThat(run.getPullRequestUrl())
                .isEqualTo("https://github.com/octocat/demo/pull/8");
        // The reason stays on the run, so the app can say why it is untested.
        assertThat(run.getErrorMessage()).contains("no tests");
    }

    @Test
    void failingTestsEndTheRunWithoutAPullRequest() {
        givenSolveOutcome("tests_failed", "Tests still failing after 3 attempts.");

        executor.execute(RUN_ID);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.TESTS_FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Tests still failing after 3 attempts.");
        assertThat(run.getResultJson()).isNotBlank();
        assertThat(run.getFinishedAt()).isNotNull();
        verify(pullRequestService, never()).open(any(), any(), any());
    }

    @Test
    void unavailableSandboxKeepsTheGeneratedChangesButOpensNoPullRequest() {
        givenSolveOutcome("sandbox_unavailable", "E2B_API_KEY is not set");

        executor.execute(RUN_ID);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.SANDBOX_UNAVAILABLE);
        assertThat(run.getErrorMessage()).isEqualTo("E2B_API_KEY is not set");
        assertThat(run.getResultJson()).isNotBlank();
        verify(pullRequestService, never()).open(any(), any(), any());
    }

    @Test
    void infrastructureErrorEndsTheRunAsFailed() {
        givenSolveOutcome("infrastructure_error", "Could not clone the repository.");

        executor.execute(RUN_ID);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("Could not clone the repository.");
        verify(pullRequestService, never()).open(any(), any(), any());
    }

    @Test
    void anExceptionInAnyStepMarksTheRunFailedInsteadOfLeavingItInProgress() {
        when(aiServiceClient.createPlan(any())).thenThrow(
                new AiServiceException("AI service is unreachable or timed out", null)
        );

        executor.execute(RUN_ID);

        assertThat(statusHistory).containsExactly(
                AgentRunStatus.ANALYZING,
                AgentRunStatus.PLANNING,
                AgentRunStatus.FAILED
        );
        assertThat(run.getErrorMessage())
                .isEqualTo("AI service is unreachable or timed out");
        assertThat(run.getFinishedAt()).isNotNull();
        verify(aiServiceClient, never()).solve(any());
    }

    @Test
    void aFailureWhileOpeningThePullRequestIsRecorded() {
        givenSolveOutcome("passed", null);
        when(pullRequestService.open(any(), any(), any()))
                .thenThrow(new IllegalStateException("GitHub rejected the branch"));

        executor.execute(RUN_ID);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("GitHub rejected the branch");
        // The tested changes are still on the run for the user to inspect.
        assertThat(run.getResultJson()).contains("\"outcome\":\"passed\"");
    }

    private void givenSolveOutcome(String outcome, String message) {
        LoopResultResponse result = new LoopResultResponse();
        result.setOutcome(outcome);
        result.setMessage(message);
        result.setFinalChanges(List.of());
        result.setAttempts(List.of());
        result.setSandboxSeconds(13.5);
        result.setEstimatedCostUsd(0.0004);

        SolveResponse response = new SolveResponse();
        response.setPlan(new EngineeringPlanResponse());
        response.setResult(result);

        when(aiServiceClient.solve(any())).thenReturn(response);
    }
}
