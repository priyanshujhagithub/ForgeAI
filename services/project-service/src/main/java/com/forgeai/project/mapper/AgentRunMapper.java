package com.forgeai.project.mapper;

import com.forgeai.project.dto.AgentRunResponse;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.entity.AgentRun;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AgentRunMapper {

    private final ObjectMapper objectMapper;

    public AgentRunMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentRunResponse toResponse(AgentRun run) {
        AgentRunResponse response = new AgentRunResponse();
        response.setId(run.getId());
        response.setProjectId(run.getProjectId());
        response.setTask(run.getTask());
        response.setStatus(run.getStatus().name());
        response.setActive(run.getStatus().isActive());

        response.setAnalysis(
                fromJson(run.getAnalysisJson(), RepositoryAnalysisResponse.class));
        response.setPlan(
                fromJson(run.getPlanJson(), EngineeringPlanResponse.class));
        response.setResult(
                fromJson(run.getResultJson(), LoopResultResponse.class));

        response.setBaseBranch(run.getBaseBranch());
        response.setBaseSha(run.getBaseSha());
        response.setBranchName(run.getBranchName());
        response.setPullRequestNumber(run.getPullRequestNumber());
        response.setPullRequestUrl(run.getPullRequestUrl());

        response.setErrorMessage(run.getErrorMessage());
        response.setSandboxSeconds(run.getSandboxSeconds());
        response.setEstimatedCostUsd(run.getEstimatedCostUsd());

        response.setCreatedAt(run.getCreatedAt());
        response.setUpdatedAt(run.getUpdatedAt());
        response.setFinishedAt(run.getFinishedAt());
        return response;
    }

    private <T> T fromJson(String json, Class<T> type) {
        return json == null ? null : objectMapper.readValue(json, type);
    }
}
