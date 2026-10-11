package com.forgeai.project.service;

// Orchestrates task-driven AI calls: fetch the repository context through
// GitHubService (which enforces ownership), then ask the Python service.

import com.forgeai.project.ai.AiServiceClient;
import com.forgeai.project.dto.AiPlanRequest;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import org.springframework.stereotype.Service;

@Service
public class AiTaskService {

    private final GitHubService gitHubService;
    private final AiServiceClient aiServiceClient;

    public AiTaskService(
            GitHubService gitHubService,
            AiServiceClient aiServiceClient
    ){
        this.gitHubService = gitHubService;
        this.aiServiceClient = aiServiceClient;
    }

    public EngineeringPlanResponse createPlan(
            Long userId,
            Long projectId,
            String task
    ){
        RepositorySnapshotContext context =
                gitHubService.getRepositoryAnalysisContext(
                        userId,
                        projectId
                );

        return aiServiceClient.createPlan(
                new AiPlanRequest(context, task, null)
        );
    }
}
