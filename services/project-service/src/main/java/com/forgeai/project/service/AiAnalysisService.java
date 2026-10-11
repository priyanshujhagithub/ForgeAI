package com.forgeai.project.service;

//  This is an orchestration service.

// GitHubService
//      ↓
//RepositorySnapshotContext
//      ↓
//AiAnalysisService
//      ↓
//AiServiceClient
//      ↓
//Python

import com.forgeai.project.ai.AiServiceClient;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import org.springframework.stereotype.Service;

@Service
public class AiAnalysisService {

    private final GitHubService gitHubService;
    private final AiServiceClient aiServiceClient;

    public AiAnalysisService(
            GitHubService gitHubService,
            AiServiceClient aiServiceClient
    ){
        this.gitHubService = gitHubService;
        this.aiServiceClient = aiServiceClient;
    }

    public RepositoryAnalysisResponse analyzeProject(
            Long userId,
            Long projectId
    ){
        RepositorySnapshotContext context=
                gitHubService.getRepositoryAnalysisContext(
                        userId,
                        projectId
                );
        return aiServiceClient.analyzeRepository(context);
    }
}
