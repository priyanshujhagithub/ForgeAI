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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GitHubService {

    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

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

    // ref pins the read to one commit. A run's diff is computed against the
    // commit the run started from, so it stays correct after the pull request
    // is merged and the branch has moved on.
    public GitHubFileResponse getProjectFile(
            Long userId,
            Long projectId,
            String path,
            String ref
    ){
        if (ref != null && !COMMIT_SHA.matcher(ref).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ref must be a commit SHA"
            );
        }

        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

        return fetchFile(
                project.getGithubOwner(),
                project.getGithubRepoName(),
                path,
                ref,
                connection.getAccessToken()
        );
    }

    public GitHubRepositorySnapshot createRepositorySnapshot(
            Long userId,
            Long projectId
    ){
        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

        return prepare(project, connection).snapshot();
    }

    // Used by agent runs: the snapshot plus the exact commit it was read at.
    public RepositoryWorkspace prepareWorkspace(
            Long userId,
            Long projectId
    ){
        Project project = connectedProject(userId, projectId);
        GitHubConnection connection = connectionFor(userId);

        PreparedRepository prepared = prepare(project, connection);

        return new RepositoryWorkspace(
                repositorySnapshotMapper.toContext(prepared.snapshot()),
                prepared.repository().getCloneUrl(),
                prepared.baseBranch(),
                prepared.baseSha(),
                prepared.repository().isPrivateRepository()
                        ? connection.getAccessToken()
                        : null
        );
    }

    private record PreparedRepository(
            GitHubRepositoryResponse repository,
            String baseBranch,
            String baseSha,
            GitHubRepositorySnapshot snapshot
    ) {}

    // Resolves the default branch to a commit, then reads every file at that commit.
    private PreparedRepository prepare(
            Project project,
            GitHubConnection connection
    ){
        String owner = project.getGithubOwner();
        String repository = project.getGithubRepoName();
        String accessToken = connection.getAccessToken();

        GitHubRepositoryResponse githubRepository =
                gitHubClient.getRepository(owner, repository, accessToken);

        String baseBranch = githubRepository.getDefaultBranch();

        String baseSha = gitHubClient.getBranchHeadSha(
                owner,
                repository,
                baseBranch,
                accessToken
        );

        List<GitHubFileResponse> files = collectFiles(
                owner,
                repository,
                baseSha,
                accessToken
        );

        return new PreparedRepository(
                githubRepository,
                baseBranch,
                baseSha,
                new GitHubRepositorySnapshot(owner, repository, files)
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

    // One tree request lists every path; only files that pass the policy and
    // fit the budget are then downloaded.
    private List<GitHubFileResponse> collectFiles(
            String owner,
            String repository,
            String commitSha,
            String accessToken
    ){
        GitHubTreeResponse tree = gitHubClient.getTree(
                owner,
                repository,
                commitSha,
                accessToken
        );

        List<GitHubFileResponse> files = new ArrayList<>();
        long totalSize = 0;

        for(GitHubTreeEntry entry : tree.getTree()){
            if (!"blob".equals(entry.getType())) {
                continue;
            }

            if (!analysisFilePolicy.shouldInclude(entry.getPath())) {
                continue;
            }

            Integer fileSize = entry.getSize();

            if (!repositorySnapshotBudget.canIncludeFile(
                    files.size(),
                    totalSize,
                    fileSize
            )) {
                continue;
            }

            GitHubBlobResponse blob = gitHubClient.getBlob(
                    owner,
                    repository,
                    entry.getSha(),
                    accessToken
            );

            files.add(new GitHubFileResponse(
                    entry.getPath(),
                    decodeBase64(blob.getContent(), blob.getEncoding()),
                    entry.getSha()
            ));
            totalSize += fileSize;
        }
        return files;
    }

    private GitHubFileResponse fetchFile(
            String owner,
            String repository,
            String path,
            String ref,
            String accessToken
    ){
        GitHubFileApiResponse githubFile =
                gitHubClient.getFile(
                        owner,
                        repository,
                        path,
                        ref,
                        accessToken
                );

        return new GitHubFileResponse(
                githubFile.getPath(),
                decodeBase64(githubFile.getContent(), githubFile.getEncoding()),
                githubFile.getSha()
        );
    }

    private String decodeBase64(String content, String encoding){
        if(!"base64".equalsIgnoreCase(encoding)){
            throw new IllegalStateException(
                    "Unsupported GitHub file encoding"
            );
        }

        return new String(
                Base64.getDecoder().decode(
                        content.replaceAll("\\s+", "")
                ),
                StandardCharsets.UTF_8
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
