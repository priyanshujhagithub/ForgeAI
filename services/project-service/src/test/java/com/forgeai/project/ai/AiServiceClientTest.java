package com.forgeai.project.ai;

import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceClientTest {

    @Test
    void shouldSendRepositoryContextAndReadAnalysisResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        ObjectMapper objectMapper = new ObjectMapper();

        MockRestServiceServer server =
                MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
                .baseUrl("http://localhost:8001")
                .build();

        AiServiceClient client =
                new AiServiceClient(restClient, objectMapper);

        RepositorySnapshotContext context =
                new RepositorySnapshotContext(
                        "octocat",
                        "Hello-World",
                        List.of(
                                new GitHubFileResponse(
                                        "README",
                                        "hello",
                                        "abc123"
                                )
                        ),
                        new RepositoryMetadata(
                                "UNKNOWN",
                                false
                        )
                );

        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/analyze"
                )
        ).andExpect(
                MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.POST
                )
        ).andExpect(
                MockRestRequestMatchers.header(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                )
        ).andExpect(
                MockRestRequestMatchers.content().json(
                        """
                        {
                          "owner": "octocat",
                          "repository": "Hello-World",
                          "files": [
                            {
                              "path": "README",
                              "content": "hello",
                              "sha": "abc123"
                            }
                          ],
                          "metadata": {
                            "projectType": "UNKNOWN",
                            "springBoot": false
                          }
                        }
                        """
                )
        ).andRespond(
                MockRestResponseCreators.withSuccess(
                        """
                        {
                          "owner": "octocat",
                          "repository": "Hello-World",
                          "projectType": "UNKNOWN",
                          "springBoot": false,
                          "fileCount": 1,
                          "files": ["README"]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                )
        );

        RepositoryAnalysisResponse response =
                client.analyzeRepository(context);

        assertEquals("octocat", response.getOwner());
        assertEquals("Hello-World", response.getRepository());
        assertEquals("UNKNOWN", response.getProjectType());
        assertFalse(response.isSpringBoot());
        assertEquals(1, response.getFileCount());
        assertEquals(List.of("README"), response.getFiles());

        server.verify();
    }
}