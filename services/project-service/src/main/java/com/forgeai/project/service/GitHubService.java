package com.forgeai.project.service;

import com.forgeai.project.dto.*;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.RepositorySnapshotMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GitHubService {

    private final GitHubConnectionRepository gitHubConnectionRepository;
    private final GitHubClient gitHubClient;
    private final ProjectRepository projectRepository;
    private final AnalysisFilePolicy analysisFilePolicy;
    private final RepositorySnapshotBudget repositorySnapshotBudget;
    private final RepositorySnapshotMapper repositorySnapshotMapper;

    public GitHubService(GitHubConnectionRepository gitHubConnectionRepository,
                         GitHubClient gitHubClient, ProjectRepository projectRepository,
                         AnalysisFilePolicy analysisFilePolicy,
                         RepositorySnapshotBudget repositorySnapshotBudget,
                         RepositorySnapshotMapper repositorySnapshotMapper
    ) {
        this.gitHubConnectionRepository = gitHubConnectionRepository;
        this.gitHubClient = gitHubClient;
        this.projectRepository = projectRepository;
        this.analysisFilePolicy = analysisFilePolicy;
        this.repositorySnapshotBudget = repositorySnapshotBudget;
        this.repositorySnapshotMapper = repositorySnapshotMapper;
    }

    public List<GitHubRepositorySummary> listRepositories(Long userID) {

        GitHubConnection connection=gitHubConnectionRepository.findByUserId(userID)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "GitHub account is not connected"
                        )
                );
        return gitHubClient.listRepositories(
                connection.getAccessToken()
        );
    }

    public List<GitHubContentResponse> getProjectRepositoryContents(
            Long userId,
            Long projectId,
            String path
    ){
        Project project = projectRepository
                .findByIdAndUserId(projectId,userId)
                .orElseThrow(()->
                        new IllegalArgumentException(
                                "Project not found"
                        )
                );

        if(project.getGithubOwner()==null
            || project.getGithubRepoName()==null){
            throw new IllegalStateException(
                    "Project is not connected to a GitHub repository"
            );
        }

        GitHubConnection connection =
                gitHubConnectionRepository.findByUserId(userId)
                .orElseThrow(()->
                                new IllegalStateException(
                                        "GitHub account is not connected"
                                )
                );

        return gitHubClient.getRepositoryContents(
                project.getGithubOwner(),
                project.getGithubRepoName(),
                path,
                connection.getAccessToken()
        );
    }

    public GitHubFileResponse getProjectFile(
            Long userId,
            Long projectId,
            String path
    ){
        Project project = projectRepository
                .findByIdAndUserId(projectId,userId)
                .orElseThrow(()->
                            new IllegalArgumentException("Project not found")
                );
        if(project.getGithubOwner()==null
            || project.getGithubRepoName()==null) {
            throw new IllegalStateException(
                    "Project is not connected to a GitHub repository"
            );
        }

        GitHubConnection connection =
                gitHubConnectionRepository.findByUserId(userId)
                        .orElseThrow(()->
                                    new IllegalStateException(
                                            "GitHub account is not connected"
                                    )
                        );

        GitHubFileApiResponse githubFile =
                gitHubClient.getFile(
                        project.getGithubOwner(),
                        project.getGithubRepoName(),
                        path,
                        connection.getAccessToken()
                );

        if(!"base64".equalsIgnoreCase(githubFile.getEncoding())){
            throw new IllegalStateException(
                    "Unsupported GitHub file encoding"
            );
        }

        String content = new String(
                java.util.Base64.getDecoder().decode(
                        githubFile.getContent().replaceAll("\\s+", "")
                ),
                java.nio.charset.StandardCharsets.UTF_8
        );

        return new GitHubFileResponse(
                githubFile.getPath(),
                content,
                githubFile.getSha()
        );
    }

    public GitHubRepositorySnapshot createRepositorySnapshot(
            Long userId,
            Long projectId
    ){
        Project project = projectRepository
                .findByIdAndUserId(projectId,userId)
                .orElseThrow(()->
                        new IllegalArgumentException("Project not found")
                );

        if(project.getGithubOwner()==null
            || project.getGithubRepoName()==null) {
            throw new IllegalStateException(
                    "GitHub repository is not connected to this project"
            );
        }

        GitHubConnection connection = gitHubConnectionRepository
                .findByUserId(userId)
                .orElseThrow(()->
                            new IllegalStateException(
                                    "GitHub account is not connected"
                            )
                );

        String owner = project.getGithubOwner();
        String repository = project.getGithubRepoName();
        String acessToken = connection.getAccessToken();

        List<GitHubFileResponse> files=new ArrayList<>();
        long[] totalSize ={0};

        collectFilesRecursively(
                userId,
                projectId,
                "",
                owner,
                repository,
                acessToken,
                files,
                totalSize
        );
        return new GitHubRepositorySnapshot(
                owner,
                repository,
                files
        );
    }

    private void collectFilesRecursively(
            Long userId,
            Long projectId,
            String path,
            String owner,
            String repository,
            String acessToken,
            List<GitHubFileResponse> files,
            long[] totalSize
    ){
        List<GitHubContentResponse> contents =
                gitHubClient.getRepositoryContents(
                        owner,
                        repository,
                        path,
                        acessToken
                );
        for(GitHubContentResponse content : contents){
            if ("file".equals(content.getType())) {

                if (!analysisFilePolicy.shouldInclude(content.getPath())) {
                    continue;
                }

                Integer fileSize = content.getSize();

                if (!repositorySnapshotBudget.canIncludeFile(
                        files.size(),
                        totalSize[0],
                        fileSize
                )) {
                    continue;
                }

                GitHubFileResponse file = getProjectFile(
                        userId,
                        projectId,
                        content.getPath()
                );

                files.add(file);
                totalSize[0] += fileSize;
            }else if("dir".equals(content.getType())){
                collectFilesRecursively(
                        userId,
                        projectId,
                        content.getPath(),
                        owner,
                        repository,
                        acessToken,
                        files,
                        totalSize
                );
            }
        }
    }

    public RepositorySnapshotContext getRepositoryAnalysisContext(
            Long userId,
            Long projectId
    ){
        GitHubRepositorySnapshot snapshot=
                createRepositorySnapshot(
                        userId,
                        projectId
                );
        return repositorySnapshotMapper.toContext(snapshot);
    }
}
