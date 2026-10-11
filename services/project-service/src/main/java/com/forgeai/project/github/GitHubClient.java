package com.forgeai.project.github;

// Talks to GitHub's HTTP API.
// So GitHubClient is basically our GitHub API adapter.

import com.forgeai.project.dto.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {
    private final RestClient restClient;

    public GitHubClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://api.github.com")
                .build();
    }

    // Unauthenticated: only sees public repositories.
    public GitHubRepositoryResponse getRepository(
            String owner,
            String repository
    ){
        return restClient.get()
                .uri("/repos/{owner}/{repository}",owner,repository)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubRepositoryResponse.class);
    }

    // Authenticated: also sees the user's private repositories.
    public GitHubRepositoryResponse getRepository(
            String owner,
            String repository,
            String accessToken
    ){
        return restClient.get()
                .uri("/repos/{owner}/{repository}",owner,repository)
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubRepositoryResponse.class);
    }

    public GitHubUserResponse getAuthenticatedUser(String accessToken) {

        return restClient.get()
                .uri("/user")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubUserResponse.class);
    }

    public List<GitHubRepositorySummary> listRepositories(String accessToken) {
        return restClient.get()
                .uri("/user/repos?per_page=100&sort=updated")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(
                        new ParameterizedTypeReference<
                                List<GitHubRepositorySummary>
                                >() {}
                );
    }

    public List<GitHubContentResponse> getRepositoryContents(
            String owner,
            String repository,
            String path,
            String accessToken
    ){
        return restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/contents/{path}",
                        owner,
                        repository,
                        path
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(
                        new ParameterizedTypeReference<
                                List<GitHubContentResponse>
                                >(){}
                );
    }

    public GitHubFileApiResponse getFile(
            String owner,
            String repository,
            String path,
            String accessToken
    ){
        return restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/contents/{path}",
                        owner,
                        repository,
                        path
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubFileApiResponse.class);
    }

    // ---- Git data API: read a repository at one exact commit ----

    public String getBranchHeadSha(
            String owner,
            String repository,
            String branch,
            String accessToken
    ){
        JsonNode ref = restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/git/ref/heads/{branch}",
                        owner,
                        repository,
                        branch
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);

        return ref.path("object").path("sha").asString();
    }

    // The whole file tree in one request, instead of one request per directory.
    public GitHubTreeResponse getTree(
            String owner,
            String repository,
            String sha,
            String accessToken
    ){
        return restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/git/trees/{sha}?recursive=1",
                        owner,
                        repository,
                        sha
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubTreeResponse.class);
    }

    public GitHubBlobResponse getBlob(
            String owner,
            String repository,
            String sha,
            String accessToken
    ){
        return restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/git/blobs/{sha}",
                        owner,
                        repository,
                        sha
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GitHubBlobResponse.class);
    }

    // ---- Git data API: write one commit on a new branch and open a pull request ----

    public String getCommitTreeSha(
            String owner,
            String repository,
            String commitSha,
            String accessToken
    ){
        JsonNode commit = restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/git/commits/{sha}",
                        owner,
                        repository,
                        commitSha
                )
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);

        return commit.path("tree").path("sha").asString();
    }

    // entries follow GitHub's tree format: {path, mode, type, content} to write
    // a file, or {path, mode, type, sha: null} to delete one.
    public String createTree(
            String owner,
            String repository,
            String baseTreeSha,
            List<Map<String, Object>> entries,
            String accessToken
    ){
        JsonNode tree = post(
                "/repos/{owner}/{repository}/git/trees",
                owner,
                repository,
                Map.of(
                        "base_tree", baseTreeSha,
                        "tree", entries
                ),
                accessToken,
                JsonNode.class
        );
        return tree.path("sha").asString();
    }

    public String createCommit(
            String owner,
            String repository,
            String message,
            String treeSha,
            String parentSha,
            String accessToken
    ){
        JsonNode commit = post(
                "/repos/{owner}/{repository}/git/commits",
                owner,
                repository,
                Map.of(
                        "message", message,
                        "tree", treeSha,
                        "parents", List.of(parentSha)
                ),
                accessToken,
                JsonNode.class
        );
        return commit.path("sha").asString();
    }

    public void createBranch(
            String owner,
            String repository,
            String branch,
            String sha,
            String accessToken
    ){
        post(
                "/repos/{owner}/{repository}/git/refs",
                owner,
                repository,
                Map.of(
                        "ref", "refs/heads/" + branch,
                        "sha", sha
                ),
                accessToken,
                JsonNode.class
        );
    }

    public GitHubPullRequestResponse createPullRequest(
            String owner,
            String repository,
            String title,
            String body,
            String headBranch,
            String baseBranch,
            String accessToken
    ){
        return post(
                "/repos/{owner}/{repository}/pulls",
                owner,
                repository,
                Map.of(
                        "title", title,
                        "body", body,
                        "head", headBranch,
                        "base", baseBranch
                ),
                accessToken,
                GitHubPullRequestResponse.class
        );
    }

    private <T> T post(
            String uriTemplate,
            String owner,
            String repository,
            Object body,
            String accessToken,
            Class<T> responseType
    ){
        return restClient.post()
                .uri(uriTemplate, owner, repository)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(responseType);
    }
}
