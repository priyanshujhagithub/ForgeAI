package com.forgeai.project.service;

import com.forgeai.project.dto.GitHubBlobResponse;
import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.GitHubRepositoryResponse;
import com.forgeai.project.dto.GitHubRepositorySnapshot;
import com.forgeai.project.dto.GitHubTreeEntry;
import com.forgeai.project.dto.GitHubTreeResponse;
import com.forgeai.project.dto.RepositoryWorkspace;
import com.forgeai.project.entity.GitHubConnection;
import com.forgeai.project.entity.Project;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.mapper.RepositorySnapshotMapper;
import com.forgeai.project.repository.GitHubConnectionRepository;
import com.forgeai.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Uses the real file policy, budget, prioritizer and metadata detector, so
// these tests cover the whole path from a GitHub tree to the AI-facing context.
class GitHubServiceSnapshotTest {

    private static final String TOKEN = "test-access-token";
    private static final String HEAD = "commit-sha-1";

    private GitHubClient gitHubClient;
    private GitHubService service;
    private GitHubRepositoryResponse githubRepository;

    @BeforeEach
    void setUp() {
        gitHubClient = mock(GitHubClient.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        GitHubConnectionRepository connectionRepository =
                mock(GitHubConnectionRepository.class);

        service = new GitHubService(
                connectionRepository,
                gitHubClient,
                projectRepository,
                new AnalysisFilePolicy(),
                new RepositorySnapshotBudget(),
                new RepositorySnapshotMapper(
                        new RepositoryMetadataDetector(),
                        new RepositoryFilePrioritizer()
                )
        );

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setGithubOwner("octocat");
        project.setGithubRepoName("demo");

        GitHubConnection connection = new GitHubConnection();
        connection.setUserId(2L);
        connection.setAccessToken(TOKEN);

        githubRepository = new GitHubRepositoryResponse();
        githubRepository.setName("demo");
        githubRepository.setDefaultBranch("main");
        githubRepository.setCloneUrl("https://github.com/octocat/demo.git");
        githubRepository.setPrivateRepository(false);

        when(projectRepository.findByIdAndUserId(5L, 2L))
                .thenReturn(Optional.of(project));
        when(connectionRepository.findByUserId(2L))
                .thenReturn(Optional.of(connection));
        when(gitHubClient.getRepository("octocat", "demo", TOKEN))
                .thenReturn(githubRepository);
        when(gitHubClient.getBranchHeadSha("octocat", "demo", "main", TOKEN))
                .thenReturn(HEAD);
    }

    @Test
    void snapshotReadsTheTreeAtTheBranchHeadAndDecodesEachFile() {
        givenTree(
                entry("src", "tree", "t1", null),
                entry("src/app.py", "blob", "b1", 11),
                entry("requirements.txt", "blob", "b2", 6)
        );
        givenBlob("b1", "print('hi')");
        givenBlob("b2", "pytest");

        GitHubRepositorySnapshot snapshot =
                service.createRepositorySnapshot(2L, 5L);

        assertThat(snapshot.getOwner()).isEqualTo("octocat");
        assertThat(snapshot.getFiles())
                .extracting(GitHubFileResponse::getPath, GitHubFileResponse::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("src/app.py", "print('hi')"),
                        org.assertj.core.groups.Tuple.tuple("requirements.txt", "pytest")
                );
        verify(gitHubClient).getTree("octocat", "demo", HEAD, TOKEN);
    }

    @Test
    void excludedAndOversizedFilesAreNeverDownloaded() {
        givenTree(
                entry("node_modules/lib/index.js", "blob", "skip1", 10),
                entry("logo.png", "blob", "skip2", 10),
                entry("huge.txt", "blob", "skip3", 600 * 1024),
                entry("README.md", "blob", "keep", 6)
        );
        givenBlob("keep", "# Demo");

        GitHubRepositorySnapshot snapshot =
                service.createRepositorySnapshot(2L, 5L);

        assertThat(snapshot.getFiles())
                .extracting(GitHubFileResponse::getPath)
                .containsExactly("README.md");
        verify(gitHubClient, times(1))
                .getBlob(anyString(), anyString(), anyString(), anyString());
        verify(gitHubClient, never()).getBlob("octocat", "demo", "skip1", TOKEN);
    }

    @Test
    void workspaceCarriesTheCommitAndPrioritisedContext() {
        givenTree(
                entry("src/app.py", "blob", "b1", 11),
                entry("requirements.txt", "blob", "b2", 6),
                entry("README.md", "blob", "b3", 6)
        );
        givenBlob("b1", "print('hi')");
        givenBlob("b2", "pytest");
        givenBlob("b3", "# Demo");

        RepositoryWorkspace workspace = service.prepareWorkspace(2L, 5L);

        assertThat(workspace.baseBranch()).isEqualTo("main");
        assertThat(workspace.baseSha()).isEqualTo(HEAD);
        assertThat(workspace.cloneUrl()).isEqualTo("https://github.com/octocat/demo.git");
        assertThat(workspace.context().getMetadata().getProjectType()).isEqualTo("PYTHON");
        // README first, then the manifest, then source.
        assertThat(workspace.context().getFiles())
                .extracting(GitHubFileResponse::getPath)
                .containsExactly("README.md", "requirements.txt", "src/app.py");
    }

    @Test
    void publicRepositoryWorkspaceCarriesNoToken() {
        givenTree();

        RepositoryWorkspace workspace = service.prepareWorkspace(2L, 5L);

        assertThat(workspace.cloneToken()).isNull();
    }

    @Test
    void privateRepositoryWorkspaceCarriesTheTokenForCloning() {
        githubRepository.setPrivateRepository(true);
        givenTree();

        RepositoryWorkspace workspace = service.prepareWorkspace(2L, 5L);

        assertThat(workspace.cloneToken()).isEqualTo(TOKEN);
    }

    private void givenTree(GitHubTreeEntry... entries) {
        GitHubTreeResponse tree = new GitHubTreeResponse();
        tree.setSha(HEAD);
        tree.setTree(List.of(entries));
        when(gitHubClient.getTree("octocat", "demo", HEAD, TOKEN)).thenReturn(tree);
    }

    private void givenBlob(String sha, String content) {
        GitHubBlobResponse blob = new GitHubBlobResponse();
        blob.setSha(sha);
        blob.setEncoding("base64");
        // GitHub wraps base64 across lines; the service must cope with that.
        blob.setContent(
                Base64.getMimeEncoder().encodeToString(
                        content.getBytes(StandardCharsets.UTF_8)
                )
        );
        when(gitHubClient.getBlob("octocat", "demo", sha, TOKEN)).thenReturn(blob);
    }

    private static GitHubTreeEntry entry(String path, String type, String sha, Integer size) {
        GitHubTreeEntry entry = new GitHubTreeEntry();
        entry.setPath(path);
        entry.setType(type);
        entry.setSha(sha);
        entry.setSize(size);
        return entry;
    }
}
