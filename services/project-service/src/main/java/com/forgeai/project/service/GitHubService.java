package com.forgeai.project.service;

import com.forgeai.project.dto.*;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.exception.GitHubNotConnectedException;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.exception.RepositoryNotConnectedException;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.RepositorySnapshotMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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

    public List<GitHubRepositorySummary> listRepositories(Long userId) {
        GitHubConnection connection = connectionFor(userId);

        return gitHubClient.listRepositories(
                connection.getAccessToken()
        );
    }

    public List<GitHubContentResponse> getProjectRepositoryContents(
            Long userId,
            Long projectId,
            String path
    ){
        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

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
        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

        return fetchFile(
                project.getGithubOwner(),
                project.getGithubRepoName(),
                path,
                connection.getAccessToken()
        );
    }

    public GitHubRepositorySnapshot createRepositorySnapshot(
            Long userId,
            Long projectId
    ){
        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

        String owner = project.getGithubOwner();
        String repository = project.getGithubRepoName();

        List<GitHubFileResponse> files=new ArrayList<>();
        long[] totalSize ={0};

        collectFilesRecursively(
                "",
                owner,
                repository,
                connection.getAccessToken(),
                files,
                totalSize
        );
        return new GitHubRepositorySnapshot(
                owner,
                repository,
                files
        );
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

    private void collectFilesRecursively(
            String path,
            String owner,
            String repository,
            String accessToken,
            List<GitHubFileResponse> files,
            long[] totalSize
    ){
        List<GitHubContentResponse> contents =
                gitHubClient.getRepositoryContents(
                        owner,
                        repository,
                        path,
                        accessToken
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

                files.add(fetchFile(
                        owner,
                        repository,
                        content.getPath(),
                        accessToken
                ));
                totalSize[0] += fileSize;
            }else if("dir".equals(content.getType())){
                collectFilesRecursively(
                        content.getPath(),
                        owner,
                        repository,
                        accessToken,
                        files,
                        totalSize
                );
            }
        }
    }

    private GitHubFileResponse fetchFile(
            String owner,
            String repository,
            String path,
            String accessToken
    ){
        GitHubFileApiResponse githubFile =
                gitHubClient.getFile(
                        owner,
                        repository,
                        path,
                        accessToken
                );

        if(!"base64".equalsIgnoreCase(githubFile.getEncoding())){
            throw new IllegalStateException(
                    "Unsupported GitHub file encoding"
            );
        }

        String content = new String(
                Base64.getDecoder().decode(
                        githubFile.getContent().replaceAll("\\s+", "")
                ),
                StandardCharsets.UTF_8
        );

        return new GitHubFileResponse(
                githubFile.getPath(),
                content,
                githubFile.getSha()
        );
    }

    // Ownership check and repository link in one place: a project the user
    // does not own is indistinguishable from one that does not exist.
    private Project connectedProject(Long userId, Long projectId){
        Project project = projectRepository
                .findByIdAndUserId(projectId,userId)
                .orElseThrow(()-> new ProjectNotFoundException(projectId));

        if(project.getGithubOwner()==null
                || project.getGithubRepoName()==null){
            throw new RepositoryNotConnectedException(projectId);
        }
        return project;
    }

    private GitHubConnection connectionFor(Long userId){
        return gitHubConnectionRepository.findByUserId(userId)
                .orElseThrow(GitHubNotConnectedException::new);
    }
}
