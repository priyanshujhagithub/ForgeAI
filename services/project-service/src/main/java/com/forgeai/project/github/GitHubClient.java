package com.forgeai.project.github;

// Talks to GitHub's HTTP API.
// So GitHubClient is basically our GitHub API adapter.

import com.forgeai.project.dto.*;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class GitHubClient {
    private final RestClient restClient;

    public GitHubClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://api.github.com")
                .build();
    }

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
                .uri("/user/repos")
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(
                        new org.springframework.core.ParameterizedTypeReference<
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
                        new org.springframework.core.ParameterizedTypeReference<
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
}
