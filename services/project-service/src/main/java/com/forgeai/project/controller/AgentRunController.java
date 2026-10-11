package com.forgeai.project.controller;

import com.forgeai.project.dto.AgentRunResponse;
import com.forgeai.project.dto.StartRunRequest;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.mapper.AgentRunMapper;
import com.forgeai.project.security.CurrentUser;
import com.forgeai.project.service.AgentRunService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final AgentRunMapper agentRunMapper;

    public AgentRunController(
            AgentRunService agentRunService,
            AgentRunMapper agentRunMapper
    ) {
        this.agentRunService = agentRunService;
        this.agentRunMapper = agentRunMapper;
    }

    // 202 Accepted: the run has been recorded and will proceed in the
    // background. Poll the Location URL for progress.
    @PostMapping("/projects/{projectId}/runs")
    public ResponseEntity<AgentRunResponse> startRun(
            @PathVariable Long projectId,
            @Valid @RequestBody StartRunRequest request
    ) {
        AgentRun run = agentRunService.start(
                CurrentUser.get().userId(),
                projectId,
                request.getTask()
        );

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/v1/runs/" + run.getId()))
                .body(agentRunMapper.toResponse(run));
    }

    @GetMapping("/runs/{runId}")
    public AgentRunResponse getRun(@PathVariable UUID runId) {
        return agentRunMapper.toResponse(
                agentRunService.get(CurrentUser.get().userId(), runId)
        );
    }

    @GetMapping("/projects/{projectId}/runs")
    public List<AgentRunResponse> listRuns(@PathVariable Long projectId) {
        return agentRunService.listForProject(CurrentUser.get().userId(), projectId)
                .stream()
                .map(agentRunMapper::toResponse)
                .toList();
    }
}
