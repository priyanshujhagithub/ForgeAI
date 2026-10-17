package com.forgeai.project.controller;

import com.forgeai.project.dto.AgentRunResponse;
import com.forgeai.project.dto.StartRunRequest;
import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.mapper.AgentRunMapper;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.security.CurrentUser;
import com.forgeai.project.service.AgentRunService;
import com.forgeai.project.service.RunAllowance;
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
    private final RunAllowance runAllowance;

    public AgentRunController(
            AgentRunService agentRunService,
            AgentRunMapper agentRunMapper,
            RunAllowance runAllowance
    ) {
        this.agentRunService = agentRunService;
        this.agentRunMapper = agentRunMapper;
        this.runAllowance = runAllowance;
    }

    // 202 Accepted: the run has been recorded and will proceed in the
    // background. Poll the Location URL for progress.
    @PostMapping("/projects/{projectId}/runs")
    public ResponseEntity<AgentRunResponse> startRun(
            @PathVariable Long projectId,
            @Valid @RequestBody StartRunRequest request
    ) {
        AuthenticatedUser user = CurrentUser.get();
        runAllowance.check(user.userId(), user.email());

        AgentRun run = agentRunService.start(
                user.userId(),
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
