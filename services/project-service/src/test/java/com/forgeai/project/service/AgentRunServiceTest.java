package com.forgeai.project.service;

import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.AgentRunAlreadyActiveException;
import com.forgeai.project.exception.AgentRunNotFoundException;
import com.forgeai.project.exception.AgentRunQueueFullException;
import com.forgeai.project.exception.GitHubNotConnectedException;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.exception.RepositoryNotConnectedException;
import com.forgeai.project.repository.AgentRunRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentRunServiceTest {

    private AgentRunRepository runRepository;
    private ProjectRepository projectRepository;
    private GitHubConnectionService connectionService;
    private AgentRunExecutor executor;
    private AgentRunService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(AgentRunRepository.class);
        projectRepository = mock(ProjectRepository.class);
        connectionService = mock(GitHubConnectionService.class);
        executor = mock(AgentRunExecutor.class);

        service = new AgentRunService(
                runRepository,
                projectRepository,
                connectionService,
                executor
        );

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setGithubOwner("octocat");
        project.setGithubRepoName("demo");

        when(projectRepository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(project));
        when(connectionService.getConnection(2L))
                .thenReturn(new GitHubConnection());
        when(runRepository.saveAndFlush(any(AgentRun.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(runRepository.save(any(AgentRun.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void start_shouldRecordAQueuedRunAndHandItToTheBackgroundWorker() {
        AgentRun run = service.start(2L, 5L, "  Add input validation  ");

        assertThat(run.getId()).isNotNull();
        assertThat(run.getProjectId()).isEqualTo(5L);
        assertThat(run.getUserId()).isEqualTo(2L);
        assertThat(run.getTask()).isEqualTo("Add input validation");
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.QUEUED);
        verify(executor).execute(run.getId());
    }

    @Test
    void start_shouldRejectAProjectOwnedBySomeoneElse() {
        assertThatThrownBy(() -> service.start(99L, 5L, "task"))
                .isInstanceOf(ProjectNotFoundException.class);

        verifyNoInteractions(runRepository, executor);
    }

    @Test
    void start_shouldRejectAProjectWithoutARepository() {
        when(projectRepository.findByIdAndUserId(6L, 2L))
                .thenReturn(Optional.of(new Project()));

        assertThatThrownBy(() -> service.start(2L, 6L, "task"))
                .isInstanceOf(RepositoryNotConnectedException.class);

        verifyNoInteractions(runRepository, executor);
    }

    @Test
    void start_shouldFailFastWhenGitHubIsNotConnected() {
        when(connectionService.getConnection(2L))
                .thenThrow(new GitHubNotConnectedException());

        assertThatThrownBy(() -> service.start(2L, 5L, "task"))
                .isInstanceOf(GitHubNotConnectedException.class);

        verifyNoInteractions(runRepository, executor);
    }

    @Test
    void start_shouldRejectASecondRunWhileOneIsInProgress() {
        when(runRepository.existsByProjectIdAndStatusIn(
                eq(5L),
                eq(AgentRunStatus.activeStatuses())
        )).thenReturn(true);

        assertThatThrownBy(() -> service.start(2L, 5L, "task"))
                .isInstanceOf(AgentRunAlreadyActiveException.class)
                .hasMessage("Project 5 already has a run in progress");

        verify(runRepository, never()).saveAndFlush(any());
        verifyNoInteractions(executor);
    }

    @Test
    void start_shouldTreatAUniqueIndexViolationAsAnActiveRun() {
        // Two requests raced past the check; the database let one in.
        when(runRepository.saveAndFlush(any(AgentRun.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.start(2L, 5L, "task"))
                .isInstanceOf(AgentRunAlreadyActiveException.class);

        verifyNoInteractions(executor);
    }

    @Test
    void start_shouldNotLeaveARunQueuedWhenTheWorkerQueueIsFull() {
        doThrow(new TaskRejectedException("queue full"))
                .when(executor).execute(any(UUID.class));

        assertThatThrownBy(() -> service.start(2L, 5L, "task"))
                .isInstanceOf(AgentRunQueueFullException.class);

        verify(runRepository).save(org.mockito.ArgumentMatchers.argThat(run ->
                run.getStatus() == AgentRunStatus.FAILED
                        && run.getFinishedAt() != null
        ));
    }

    @Test
    void get_shouldOnlyReturnRunsOwnedByTheUser() {
        UUID runId = UUID.randomUUID();
        when(runRepository.findByIdAndUserId(runId, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L, runId))
                .isInstanceOf(AgentRunNotFoundException.class)
                .hasMessage("Run not found with id: " + runId);
    }

    @Test
    void listForProject_shouldCheckOwnershipFirst() {
        assertThatThrownBy(() -> service.listForProject(99L, 5L))
                .isInstanceOf(ProjectNotFoundException.class);

        verifyNoInteractions(runRepository);
    }

    @Test
    void restart_shouldCloseOutRunsThatWereInFlight() {
        AgentRun stuck = new AgentRun();
        stuck.setId(UUID.randomUUID());
        stuck.setStatus(AgentRunStatus.GENERATING_AND_TESTING);

        when(runRepository.findByStatusIn(AgentRunStatus.activeStatuses()))
                .thenReturn(List.of(stuck));

        service.failRunsInterruptedByRestart();

        assertThat(stuck.getStatus()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(stuck.getErrorMessage()).isEqualTo("Interrupted by a service restart");
        assertThat(stuck.getFinishedAt()).isNotNull();
        verify(runRepository).save(stuck);
    }
}
