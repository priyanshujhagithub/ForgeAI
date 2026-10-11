package com.forgeai.project.github;

import com.forgeai.project.dto.GitHubRepositoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubClientTest {

    @Test
    void getRepository_shouldFetchPublicRepository() {

        RestClient.Builder builder = RestClient.builder();

        GitHubClient gitHubClient = new GitHubClient(builder);

        GitHubRepositoryResponse response =
                gitHubClient.getRepository("octocat", "Hello-World");

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Hello-World");
        assertThat(response.getFullName())
                .isEqualTo("octocat/Hello-World");
        assertThat(response.getHtmlUrl()).isNotBlank();
        assertThat(response.getOwner()).isNotNull();
        assertThat(response.getOwner().getLogin())
                .isEqualTo("octocat");
    }
}