package com.forgeai.project.github;

import com.forgeai.project.dto.GitHubPullRequestResponse;
import com.forgeai.project.dto.GitHubRepositoryResponse;
import com.forgeai.project.dto.GitHubTreeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// GitHub is replaced by a mock server: these tests check the requests we send
// and how we read the replies, without depending on the network.
class GitHubClientTest {

    private static final String API = "https://api.github.com";
    private static final String TOKEN = "test-access-token";

    private MockRestServiceServer server;
    private GitHubClient gitHubClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gitHubClient = new GitHubClient(builder);
    }

    @Test
    void getRepository_shouldReadBranchAndCloneUrlWithTheUsersToken() {
        server.expect(requestTo(API + "/repos/octocat/demo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        """
                        {
                          "name": "demo",
                          "full_name": "octocat/demo",
                          "html_url": "https://github.com/octocat/demo",
                          "clone_url": "https://github.com/octocat/demo.git",
                          "default_branch": "main",
                          "private": true,
                          "owner": {"login": "octocat"},
                          "stargazers_count": 3
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        GitHubRepositoryResponse repository =
                gitHubClient.getRepository("octocat", "demo", TOKEN);

        assertThat(repository.getName()).isEqualTo("demo");
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        assertThat(repository.getCloneUrl()).isEqualTo("https://github.com/octocat/demo.git");
        assertThat(repository.isPrivateRepository()).isTrue();
        assertThat(repository.getOwner().getLogin()).isEqualTo("octocat");
        server.verify();
    }

    @Test
    void getBranchHeadSha_shouldReadTheCommitTheBranchPointsAt() {
        server.expect(requestTo(API + "/repos/octocat/demo/git/ref/heads/main"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"ref": "refs/heads/main", "object": {"type": "commit", "sha": "abc123"}}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(gitHubClient.getBranchHeadSha("octocat", "demo", "main", TOKEN))
                .isEqualTo("abc123");
        server.verify();
    }

    @Test
    void getTree_shouldAskForTheWholeTreeInOneRequest() {
        server.expect(requestTo(API + "/repos/octocat/demo/git/trees/abc123?recursive=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {
                          "sha": "abc123",
                          "truncated": false,
                          "tree": [
                            {"path": "app", "mode": "040000", "type": "tree", "sha": "t1"},
                            {"path": "app/main.py", "mode": "100644", "type": "blob",
                             "sha": "b1", "size": 479}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        GitHubTreeResponse tree = gitHubClient.getTree("octocat", "demo", "abc123", TOKEN);

        assertThat(tree.getTree()).hasSize(2);
        assertThat(tree.getTree().get(1).getPath()).isEqualTo("app/main.py");
        assertThat(tree.getTree().get(1).getType()).isEqualTo("blob");
        assertThat(tree.getTree().get(1).getSize()).isEqualTo(479);
        server.verify();
    }

    @Test
    void getFile_shouldReadAtTheGivenCommit() {
        server.expect(requestTo(API + "/repos/octocat/demo/contents/README.md?ref=abc1234"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(
                        """
                        {"path": "README.md", "sha": "b1", "encoding": "base64", "content": "aGk="}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(gitHubClient.getFile("octocat", "demo", "README.md", "abc1234", TOKEN).getSha())
                .isEqualTo("b1");
        server.verify();
    }

    @Test
    void getFile_shouldReadTheDefaultBranchWhenNoCommitIsGiven() {
        server.expect(requestTo(API + "/repos/octocat/demo/contents/README.md"))
                .andRespond(withSuccess(
                        """
                        {"path": "README.md", "sha": "b2", "encoding": "base64", "content": "aGk="}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertThat(gitHubClient.getFile("octocat", "demo", "README.md", null, TOKEN).getSha())
                .isEqualTo("b2");
        server.verify();
    }

    @Test
    void createTree_shouldSendContentForWritesAndNullShaForDeletes() {
        Map<String, Object> write = new HashMap<>();
        write.put("path", "app/main.py");
        write.put("mode", "100644");
        write.put("type", "blob");
        write.put("content", "print('hi')\n");

        Map<String, Object> delete = new HashMap<>();
        delete.put("path", "old.py");
        delete.put("mode", "100644");
        delete.put("type", "blob");
        delete.put("sha", null);

        server.expect(requestTo(API + "/repos/octocat/demo/git/trees"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json(
                        """
                        {
                          "base_tree": "base-tree-sha",
                          "tree": [
                            {"path": "app/main.py", "mode": "100644", "type": "blob",
                             "content": "print('hi')\\n"},
                            {"path": "old.py", "mode": "100644", "type": "blob", "sha": null}
                          ]
                        }
                        """
                ))
                .andRespond(withSuccess(
                        """
                        {"sha": "new-tree-sha"}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        String treeSha = gitHubClient.createTree(
                "octocat", "demo", "base-tree-sha", List.of(write, delete), TOKEN
        );

        assertThat(treeSha).isEqualTo("new-tree-sha");
        server.verify();
    }

    @Test
    void createCommitAndBranch_shouldBuildOnTheBaseCommit() {
        server.expect(requestTo(API + "/repos/octocat/demo/git/commits"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        """
                        {"message": "Add validation", "tree": "new-tree-sha", "parents": ["abc123"]}
                        """
                ))
                .andRespond(withSuccess(
                        """
                        {"sha": "new-commit-sha"}
                        """,
                        MediaType.APPLICATION_JSON
                ));
        server.expect(requestTo(API + "/repos/octocat/demo/git/refs"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        """
                        {"ref": "refs/heads/forgeai/task-1234abcd", "sha": "new-commit-sha"}
                        """
                ))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String commitSha = gitHubClient.createCommit(
                "octocat", "demo", "Add validation", "new-tree-sha", "abc123", TOKEN
        );
        gitHubClient.createBranch(
                "octocat", "demo", "forgeai/task-1234abcd", commitSha, TOKEN
        );

        assertThat(commitSha).isEqualTo("new-commit-sha");
        server.verify();
    }

    @Test
    void createPullRequest_shouldTargetTheBaseBranchAndReturnTheUrl() {
        server.expect(requestTo(API + "/repos/octocat/demo/pulls"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json(
                        """
                        {
                          "title": "Add validation",
                          "body": "Details",
                          "head": "forgeai/task-1234abcd",
                          "base": "main"
                        }
                        """
                ))
                .andRespond(withSuccess(
                        """
                        {"number": 7, "html_url": "https://github.com/octocat/demo/pull/7"}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        GitHubPullRequestResponse pullRequest = gitHubClient.createPullRequest(
                "octocat", "demo", "Add validation", "Details",
                "forgeai/task-1234abcd", "main", TOKEN
        );

        assertThat(pullRequest.getNumber()).isEqualTo(7);
        assertThat(pullRequest.getHtmlUrl())
                .isEqualTo("https://github.com/octocat/demo/pull/7");
        server.verify();
    }
}
