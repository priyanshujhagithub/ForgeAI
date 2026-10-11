package com.forgeai.project.service;

import com.forgeai.project.dto.CreateProjectRequest;
import com.forgeai.project.dto.GitHubOwnerResponse;
import com.forgeai.project.dto.GitHubRepositoryResponse;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.ProjectMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class ProjectServiceTest {

    @Test
    void createProject_shouldCreateAndSaveProject() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        CreateProjectRequest request = new CreateProjectRequest();

        request.setName("ForgeAI");
        request.setDescription("AI coding platform");

        Project savedProject = new Project();
        savedProject.setUserId(1L);
        savedProject.setName("ForgeAI");
        savedProject.setDescription("AI coding platform");

        when(repository.save(any(Project.class)))
                .thenReturn(savedProject);

        Project result = service.createProject(request,1L);

        assertThat(result).isSameAs(savedProject);
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("ForgeAI");
        assertThat(result.getDescription()).isEqualTo("AI coding platform");

        verify(repository).save(any(Project.class));
    }
    @Test
    void getProject_shouldReturnProject() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        Project project = new Project();
        project.setId(4L);
        project.setUserId(1L);
        project.setName("ForgeAI");

        when(repository.findByIdAndUserId(4L,1L))
                .thenReturn(Optional.of(project));

        Project result = service.getProject(4L,1L);

        assertThat(result).isSameAs(project);
        verify(repository).findByIdAndUserId(4L,1L);
    }
    @Test
    void getProjectsForUser_shouldReturnUserProjects() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        Project project1 = new Project();
        project1.setId(1L);
        project1.setUserId(1L);
        project1.setName("ForgeAI");

        Project project2 = new Project();
        project2.setId(2L);
        project2.setUserId(1L);
        project2.setName("Website");

        when(repository.findByUserId(1L))
                .thenReturn(List.of(project1, project2));

        List<Project> result = service.getProjectsForUser(1L);

        assertThat(result)
                .hasSize(2)
                .containsExactly(project1, project2);

        verify(repository).findByUserId(1L);
    }
    @Test
    void deleteProject_shouldDeleteExistingProject() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        Project project = new Project();
        project.setId(6L);
        project.setUserId(2L);

        when(repository.findByIdAndUserId(6L, 2L))
                .thenReturn(Optional.of(project));

        service.deleteProject(6L, 2L);

        verify(repository).findByIdAndUserId(6L, 2L);
        verify(repository).delete(project);
    }
    @Test
    void deleteProject_shouldThrowWhenProjectDoesNotBelongToUser() {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        when(repository.findByIdAndUserId(6L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProject(6L, 2L))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found with id: 6");

        verify(repository).findByIdAndUserId(6L, 2L);
        verify(repository, never()).delete(any(Project.class));
    }
    @Test
    void connectGitHubRepository_shouldConnectRepositoryToProject() {

        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = new ProjectMapper();
        GitHubClient gitHubClient = mock(GitHubClient.class);

        ProjectService service =
                new ProjectService(
                        repository,
                        mapper,
                        gitHubClient,
                        mock(GitHubConnectionRepository.class)
                );

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setName("User2 Project");

        GitHubOwnerResponse owner = new GitHubOwnerResponse();
        owner.setLogin("octocat");

        GitHubRepositoryResponse githubRepository =
                new GitHubRepositoryResponse();

        githubRepository.setName("Hello-World");
        githubRepository.setFullName("octocat/Hello-World");
        githubRepository.setHtmlUrl(
                "https://github.com/octocat/Hello-World"
        );
        githubRepository.setOwner(owner);
        githubRepository.setPrivateRepository(false);

        when(repository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(project));

        when(gitHubClient.getRepository("octocat", "Hello-World"))
                .thenReturn(githubRepository);

        when(repository.save(project))
                .thenReturn(project);

        Project result = service.connectGitHubRepository(
                5L,
                2L,
                "octocat",
                "Hello-World"
        );

        assertThat(result.getGithubRepoName())
                .isEqualTo("Hello-World");

        assertThat(result.getGithubOwner())
                .isEqualTo("octocat");

        assertThat(result.getGithubRepoUrl())
                .isEqualTo("https://github.com/octocat/Hello-World");

        verify(repository)
                .findByIdAndUserId(5L, 2L);

        verify(gitHubClient)
                .getRepository("octocat", "Hello-World");

        verify(repository)
                .save(project);
    }
}