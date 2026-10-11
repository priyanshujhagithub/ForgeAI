package com.forgeai.project.service;

// Runs one agent run in the background, from repository to pull request.
//
// A run takes minutes (longer on a local model), so it cannot live inside an
// HTTP request. Each step is saved to the agent_runs row as it completes; the
// row is what clients poll and what survives if this process dies mid-run.

import com.forgeai.project.ai.AiServiceClient;
import com.forgeai.project.dto.AiPlanRequest;
import com.forgeai.project.dto.AiRepositoryRef;
import com.forgeai.project.dto.AiSolveRequest;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositoryWorkspace;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.repository.AgentRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class AgentRunExecutor {

    private static final Logger log =
            LoggerFactory.getLogger(AgentRunExecutor.class);

    private final AgentRunRepository agentRunRepository;
    private final GitHubService gitHubService;
    private final AiServiceClient aiServiceClient;
    private final PullRequestService pullRequestService;
    private final ObjectMapper objectMapper;

    public AgentRunExecutor(
            AgentRunRepository agentRunRepository,
            GitHubService gitHubService,
            AiServiceClient aiServiceClient,
            PullRequestService pullRequestService,
            ObjectMapper objectMapper
    ) {
        this.agentRunRepository = agentRunRepository;
        this.gitHubService = gitHubService;
        this.aiServiceClient = aiServiceClient;
        this.pullRequestService = pullRequestService;
        this.objectMapper = objectMapper;
    }

    @Async("agentRunTaskExecutor")
    public void execute(UUID runId) {
        try {
            run(runId);
        } catch (Exception e) {
            // Whatever went wrong, the run must not stay "in progress" forever.
            log.error("Agent run {} failed", runId, e);
            finish(runId, AgentRunStatus.FAILED, e.getMessage());
        }
    }

    private void run(UUID runId) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow();

        // 1. Read the repository once, at one commit. Every later step uses this.
        update(runId, r -> r.setStatus(AgentRunStatus.ANALYZING));
        RepositoryWorkspace workspace =
                gitHubService.prepareWorkspace(run.getUserId(), run.getProjectId());
        update(runId, r -> {
            r.setBaseBranch(workspace.baseBranch());
            r.setBaseSha(workspace.baseSha());
        });

        RepositoryAnalysisResponse analysis =
                aiServiceClient.analyzeRepository(workspace.context());
        update(runId, r -> r.setAnalysisJson(toJson(analysis)));

        // 2. Plan.
        update(runId, r -> r.setStatus(AgentRunStatus.PLANNING));
        EngineeringPlanResponse plan = aiServiceClient.createPlan(
                new AiPlanRequest(workspace.context(), run.getTask(), analysis)
        );
        update(runId, r -> r.setPlanJson(toJson(plan)));

        // 3. Generate, test in the sandbox, self-correct.
        update(runId, r -> r.setStatus(AgentRunStatus.GENERATING_AND_TESTING));
        LoopResultResponse result = aiServiceClient.solve(
                new AiSolveRequest(
                        workspace.context(),
                        run.getTask(),
                        new AiRepositoryRef(
                                workspace.cloneUrl(),
                                workspace.baseBranch(),
                                workspace.baseSha(),
                                workspace.cloneToken()
                        ),
                        plan,
                        analysis
                )
        ).getResult();
        update(runId, r -> {
            r.setResultJson(toJson(result));
            r.setSandboxSeconds(result.getSandboxSeconds());
            r.setEstimatedCostUsd(result.getEstimatedCostUsd());
        });

        // 4. Only changes whose tests passed are proposed to the repository.
        switch (result.getOutcome()) {
            case "passed" -> openPullRequest(runId, plan, result);
            case "tests_failed" ->
                    finish(runId, AgentRunStatus.TESTS_FAILED, result.getMessage());
            case "sandbox_unavailable" ->
                    finish(runId, AgentRunStatus.SANDBOX_UNAVAILABLE, result.getMessage());
            default ->
                    finish(runId, AgentRunStatus.FAILED, result.getMessage());
        }
    }

    private void openPullRequest(
            UUID runId,
            EngineeringPlanResponse plan,
            LoopResultResponse result
    ) {
        AgentRun run = update(runId, r -> r.setStatus(AgentRunStatus.OPENING_PR));

        PullRequestService.OpenedPullRequest pullRequest =
                pullRequestService.open(run, plan, result);

        update(runId, r -> {
            r.setBranchName(pullRequest.branchName());
            r.setPullRequestNumber(pullRequest.number());
            r.setPullRequestUrl(pullRequest.url());
        });
        finish(runId, AgentRunStatus.COMPLETED, null);
    }

    private void finish(UUID runId, AgentRunStatus status, String message) {
        update(runId, r -> {
            r.setStatus(status);
            r.setErrorMessage(message);
            r.setFinishedAt(LocalDateTime.now());
        });
    }

    // Load, change, save: each step is its own short transaction, so progress
    // is visible to polling clients immediately.
    private AgentRun update(UUID runId, Consumer<AgentRun> change) {
        AgentRun run = agentRunRepository.findById(runId).orElseThrow();
        change.accept(run);
        return agentRunRepository.save(run);
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
