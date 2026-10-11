package com.forgeai.project.service;

import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.mapper.RepositorySnapshotMapper;
import com.forgeai.project.dto.GitHubContentResponse;
import com.forgeai.project.dto.GitHubRepositorySnapshot;
import com.forgeai.project.dto.GitHubRepositorySummary;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.GitHubNotConnectedException;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.RepositorySnapshotMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GitHubServiceTest {

    @Test
    void listRepositories_shouldUseUsersGitHubConnection() {

        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        GitHubClient gitHubClient =
                mock(GitHubClient.class);

        ProjectRepository projectRepository =
                mock(ProjectRepository.class);

        AnalysisFilePolicy analysisFilePolicy =
                mock(AnalysisFilePolicy.class);

        RepositorySnapshotBudget repositorySnapshotBudget=
                mock(RepositorySnapshotBudget.class);

        RepositorySnapshotMapper repositorySnapshotMapper=
                mock(RepositorySnapshotMapper.class);

        GitHubService service =
                new GitHubService(
                        connectionRepository,
                        gitHubClient,
                        projectRepository,
                        analysisFilePolicy,
                        repositorySnapshotBudget,
                        repositorySnapshotMapper
                );

        GitHubConnection connection =
                new GitHubConnection();

        connection.setUserId(2L);
        connection.setGithubUserId(12345678L);
        connection.setGithubUsername("priyanshujhagithub");
        connection.setAccessToken("test-access-token");

        GitHubRepositorySummary repository =
                new GitHubRepositorySummary();

        repository.setId(1L);
        repository.setName("forgeai");
        repository.setFullName("priyanshujhagithub/forgeai");
        repository.setHtmlUrl(
                "https://github.com/priyanshujhagithub/forgeai"
        );
        repository.setPrivateRepository(true);

        when(connectionRepository.findByUserId(2L))
                .thenReturn(Optional.of(connection));

        when(gitHubClient.listRepositories("test-access-token"))
                .thenReturn(List.of(repository));

        when(analysisFilePolicy.shouldInclude(anyString()))
                .thenReturn(true);

        when(repositorySnapshotBudget.canIncludeFile(
                anyInt(),
                anyLong(),
                any()
        )).thenReturn(true);

        List<GitHubRepositorySummary> result =
                service.listRepositories(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName())
                .isEqualTo("forgeai");
        assertThat(result.get(0).getFullName())
                .isEqualTo("priyanshujhagithub/forgeai");

        verify(connectionRepository)
                .findByUserId(2L);

        verify(gitHubClient)
                .listRepositories("test-access-token");
    }

    @Test
    void listRepositories_shouldFailWhenGitHubIsNotConnected() {

        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        GitHubClient gitHubClient =
                mock(GitHubClient.class);

        ProjectRepository projectRepository =
                mock(ProjectRepository.class);

        AnalysisFilePolicy analysisFilePolicy =
                mock(AnalysisFilePolicy.class);

        RepositorySnapshotBudget repositorySnapshotBudget=
                mock(RepositorySnapshotBudget.class);

        RepositorySnapshotMapper repositorySnapshotMapper=
                mock(RepositorySnapshotMapper.class);

        GitHubService service =
                new GitHubService(
                        connectionRepository,
                        gitHubClient,
                        projectRepository,
                        analysisFilePolicy,
                        repositorySnapshotBudget,
                        repositorySnapshotMapper
                );

        when(connectionRepository.findByUserId(2L))
                .thenReturn(Optional.empty());

        when(analysisFilePolicy.shouldInclude(anyString()))
                .thenReturn(true);

        when(repositorySnapshotBudget.canIncludeFile(
                anyInt(),
                anyLong(),
                any()
        )).thenReturn(true);

        assertThat(
                Assertions.catchThrowable(
                        () -> service.listRepositories(2L)
                )
        )
                .isInstanceOf(GitHubNotConnectedException.class)
                .hasMessage("GitHub account is not connected");

        verifyNoInteractions(gitHubClient);
    }

    @Test
    void getProjectRepositoryContents_shouldUseProjectRepositoryAndGitHubConnection() {

        ProjectRepository projectRepository =
                mock(ProjectRepository.class);

        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        GitHubClient gitHubClient =
                mock(GitHubClient.class);

        AnalysisFilePolicy analysisFilePolicy =
                mock(AnalysisFilePolicy.class);

        RepositorySnapshotBudget repositorySnapshotBudget=
                mock(RepositorySnapshotBudget.class);

        RepositorySnapshotMapper repositorySnapshotMapper=
                mock(RepositorySnapshotMapper.class);

        GitHubService service =
                new GitHubService(
                        connectionRepository,
                        gitHubClient,
                        projectRepository,
                        analysisFilePolicy,
                        repositorySnapshotBudget,
                        repositorySnapshotMapper
                );

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setGithubOwner("octocat");
        project.setGithubRepoName("Hello-World");

        GitHubConnection connection = new GitHubConnection();
        connection.setUserId(2L);
        connection.setAccessToken("test-access-token");

        GitHubContentResponse content = new GitHubContentResponse();
        content.setName("README.md");
        content.setPath("README.md");
        content.setType("file");

        when(projectRepository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(project));

        when(connectionRepository.findByUserId(2L))
                .thenReturn(Optional.of(connection));

        when(gitHubClient.getRepositoryContents(
                "octocat",
                "Hello-World",
                "",
                "test-access-token"
        )).thenReturn(List.of(content));

        when(analysisFilePolicy.shouldInclude(anyString()))
                .thenReturn(true);

        when(repositorySnapshotBudget.canIncludeFile(
                anyInt(),
                anyLong(),
                any()
        )).thenReturn(true);

        List<GitHubContentResponse> result =
                service.getProjectRepositoryContents(
                        2L,
                        5L,
                        ""
                );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName())
                .isEqualTo("README.md");

        verify(projectRepository)
                .findByIdAndUserId(5L, 2L);

        verify(connectionRepository)
                .findByUserId(2L);

        verify(gitHubClient)
                .getRepositoryContents(
                        "octocat",
                        "Hello-World",
                        "",
                        "test-access-token"
                );
    }

    @Test
    void getProjectRepositoryContents_shouldFailWhenProjectDoesNotBelongToUser() {

        ProjectRepository projectRepository =
                mock(ProjectRepository.class);

        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        GitHubClient gitHubClient =
                mock(GitHubClient.class);

        AnalysisFilePolicy analysisFilePolicy =
                mock(AnalysisFilePolicy.class);

        RepositorySnapshotBudget repositorySnapshotBudget=
                mock(RepositorySnapshotBudget.class);

        RepositorySnapshotMapper repositorySnapshotMapper=
                mock(RepositorySnapshotMapper.class);

        GitHubService service =
                new GitHubService(
                        connectionRepository,
                        gitHubClient,
                        projectRepository,
                        analysisFilePolicy,
                        repositorySnapshotBudget,
                        repositorySnapshotMapper
                );

        when(projectRepository.findByIdAndUserId(5L, 99L))
                .thenReturn(Optional.empty());

        when(analysisFilePolicy.shouldInclude(anyString()))
                .thenReturn(true);

        when(repositorySnapshotBudget.canIncludeFile(
                anyInt(),
                anyLong(),
                any()
        )).thenReturn(true);

        assertThat(
                Assertions.catchThrowable(
                        () -> service.getProjectRepositoryContents(
                                99L,
                                5L,
                                ""
                        )
                )
        )
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found with id: 5");

        verifyNoInteractions(connectionRepository);
        verifyNoInteractions(gitHubClient);
    }

    @Test
    void shouldCreateRepositoryAnalysisContext() {

        ProjectRepository projectRepository =
                mock(ProjectRepository.class);

        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        GitHubClient gitHubClient =
                mock(GitHubClient.class);

        AnalysisFilePolicy analysisFilePolicy =
                mock(AnalysisFilePolicy.class);

        RepositorySnapshotBudget repositorySnapshotBudget =
                mock(RepositorySnapshotBudget.class);

        RepositorySnapshotMapper repositorySnapshotMapper =
                mock(RepositorySnapshotMapper.class);

        GitHubService service =
                new GitHubService(
                        connectionRepository,
                        gitHubClient,
                        projectRepository,
                        analysisFilePolicy,
                        repositorySnapshotBudget,
                        repositorySnapshotMapper
                );

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setGithubOwner("octocat");
        project.setGithubRepoName("Hello-World");

        GitHubConnection connection = new GitHubConnection();
        connection.setUserId(2L);
        connection.setAccessToken("test-access-token");

        GitHubRepositorySnapshot snapshot =
                new GitHubRepositorySnapshot(
                        "octocat",
                        "Hello-World",
                        List.of()
                );

        RepositorySnapshotContext context =
                new RepositorySnapshotContext(
                        "octocat",
                        "Hello-World",
                        List.of(),
                        new RepositoryMetadata(
                                "UNKNOWN",
                                false
                        )
                );

        when(projectRepository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(project));

        when(connectionRepository.findByUserId(2L))
                .thenReturn(Optional.of(connection));

        when(gitHubClient.getRepositoryContents(
                "octocat",
                "Hello-World",
                "",
                "test-access-token"
        )).thenReturn(List.of());

        when(repositorySnapshotMapper.toContext(any(
                GitHubRepositorySnapshot.class
        ))).thenReturn(context);

        RepositorySnapshotContext result =
                service.getRepositoryAnalysisContext(
                        2L,
                        5L
                );

        assertThat(result).isSameAs(context);

        verify(repositorySnapshotMapper)
                .toContext(any(GitHubRepositorySnapshot.class));
    }
}