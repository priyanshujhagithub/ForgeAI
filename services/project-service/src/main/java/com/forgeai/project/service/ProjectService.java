package com.forgeai.project.service;

import com.forgeai.project.dto.CreateProjectRequest;
import com.forgeai.project.dto.GitHubRepositoryResponse;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.ProjectMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final GitHubClient gitHubClient;
    private final GitHubConnectionRepository gitHubConnectionRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper, GitHubClient gitHubClient,
                          GitHubConnectionRepository gitHubConnectionRepository) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.gitHubClient = gitHubClient;
        this.gitHubConnectionRepository = gitHubConnectionRepository;
    }

    public Project createProject(CreateProjectRequest request,Long userId){
        Project project = projectMapper.toEntity(request,userId);
        return projectRepository.save(project);
    }

    public Project getProject(Long id,Long userId){
        return projectRepository.findByIdAndUserId(id,userId).orElseThrow(()-> new ProjectNotFoundException(id));
    }

    public List<Project> getProjectsForUser(Long userId){
        return projectRepository.findByUserId(userId);
    }

    public void deleteProject(Long id,Long userId){
        Project project=projectRepository.findByIdAndUserId(id,userId).orElseThrow(() -> new ProjectNotFoundException(id));
        projectRepository.delete(project);
    }

    public Project connectGitHubRepository(
            Long projectId,
            Long userId,
            String owner,
            String repository
    ){
        Project project=projectRepository.findByIdAndUserId(projectId,userId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        // With a connected GitHub account the lookup is authenticated, so the
        // user's private repositories can be linked too.
        GitHubRepositoryResponse githubRepository = gitHubConnectionRepository
                .findByUserId(userId)
                .map(connection -> gitHubClient.getRepository(
                        owner,
                        repository,
                        connection.getAccessToken()
                ))
                .orElseGet(() -> gitHubClient.getRepository(owner,repository));
        project.setGithubRepoUrl(githubRepository.getHtmlUrl());
        project.setGithubRepoName(githubRepository.getName());
        project.setGithubOwner(githubRepository.getOwner().getLogin());
        return projectRepository.save(project);
    }
}