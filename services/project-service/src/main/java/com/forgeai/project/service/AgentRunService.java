package com.forgeai.project.service;

// Starts agent runs and answers questions about them.
// The work itself happens in AgentRunExecutor.

import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.AgentRunAlreadyActiveException;
import com.forgeai.project.exception.AgentRunNotFoundException;
import com.forgeai.project.exception.AgentRunQueueFullException;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.exception.RepositoryNotConnectedException;
import com.forgeai.project.repository.AgentRunRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AgentRunService {

    private static final Logger log =
            LoggerFactory.getLogger(AgentRunService.class);

    private final AgentRunRepository agentRunRepository;
    private final ProjectRepository projectRepository;
    private final GitHubConnectionService gitHubConnectionService;
    private final AgentRunExecutor agentRunExecutor;

    public AgentRunService(
            AgentRunRepository agentRunRepository,
            ProjectRepository projectRepository,
            GitHubConnectionService gitHubConnectionService,
            AgentRunExecutor agentRunExecutor
    ) {
        this.agentRunRepository = agentRunRepository;
        this.projectRepository = projectRepository;
        this.gitHubConnectionService = gitHubConnectionService;
        this.agentRunExecutor = agentRunExecutor;
    }

    // Validates everything that can be checked cheaply, records the run, and
    // returns at once. The caller polls the run for progress.
    public AgentRun start(Long userId, Long projectId, String task) {
        Project project = projectRepository
                .findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getGithubOwner() == null
                || project.getGithubRepoName() == null) {
            throw new RepositoryNotConnectedException(projectId);
        }

        // Fails now with a clear message, not minutes later inside the run.
        gitHubConnectionService.getConnection(userId);

        if (agentRunRepository.existsByProjectIdAndStatusIn(
                projectId,
                AgentRunStatus.activeStatuses()
        )) {
            throw new AgentRunAlreadyActiveException(projectId);
        }

        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setUserId(userId);
        run.setTask(task.strip());
        run.setStatus(AgentRunStatus.QUEUED);

        try {
            run = agentRunRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException e) {
            // Two requests passed the check above together; the unique index
            // let only one of them in.
            throw new AgentRunAlreadyActiveException(projectId);
        }

        try {
            agentRunExecutor.execute(run.getId());
        } catch (TaskRejectedException e) {
            run.setStatus(AgentRunStatus.FAILED);
            run.setErrorMessage("Run queue was full");
            run.setFinishedAt(LocalDateTime.now());
            agentRunRepository.save(run);
            throw new AgentRunQueueFullException();
        }
        return run;
    }

    public AgentRun get(Long userId, UUID runId) {
        return agentRunRepository
                .findByIdAndUserId(runId, userId)
                .orElseThrow(() -> new AgentRunNotFoundException(runId));
    }

    public List<AgentRun> listForProject(Long userId, Long projectId) {
        projectRepository
                .findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        return agentRunRepository
                .findByProjectIdAndUserIdOrderByCreatedAtDesc(projectId, userId);
    }

    // Runs live in this process. If it restarted, any run still marked as in
    // flight has no worker left, so it is closed out instead of hanging forever.
    @EventListener(ApplicationReadyEvent.class)
    public void failRunsInterruptedByRestart() {
        List<AgentRun> interrupted =
                agentRunRepository.findByStatusIn(AgentRunStatus.activeStatuses());

        for (AgentRun run : interrupted) {
            run.setStatus(AgentRunStatus.FAILED);
            run.setErrorMessage("Interrupted by a service restart");
            run.setFinishedAt(LocalDateTime.now());
            agentRunRepository.save(run);
        }

        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted agent run(s) as FAILED", interrupted.size());
        }
    }
}
