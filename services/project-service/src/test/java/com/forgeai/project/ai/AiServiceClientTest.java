package com.forgeai.project.ai;

import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceClientTest {

    private MockRestServiceServer server;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();

        // Bind the mock server to the same builder the client is built from,
        // otherwise the mock never sees the request.
        server = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder
                .baseUrl("http://localhost:8001")
                .build();

        client = new AiServiceClient(restClient, new ObjectMapper());
    }

    @Test
    void shouldSendRepositoryContextAndReadAnalysisResponse() {
        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/analyze"
                )
        ).andExpect(
                MockRestRequestMatchers.method(HttpMethod.POST)
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
                          "summary": "A greeting repository.",
                          "projectType": "Documentation",
                          "keyComponents": ["README"],
                          "dependencies": [],
                          "architectureNotes": "A single README file.",
                          "relevantFiles": ["README"]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                )
        );

        RepositoryAnalysisResponse response =
                client.analyzeRepository(context());

        assertEquals("A greeting repository.", response.getSummary());
        assertEquals("Documentation", response.getProjectType());
        assertEquals(List.of("README"), response.getKeyComponents());
        assertEquals(List.of(), response.getDependencies());
        assertEquals("A single README file.", response.getArchitectureNotes());
        assertEquals(List.of("README"), response.getRelevantFiles());

        server.verify();
    }

    @Test
    void shouldSurfaceThePythonErrorDetailWhenTheAiServiceFails() {
        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/analyze"
                )
        ).andRespond(
                MockRestResponseCreators.withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": "Model output failed validation"}
                                """)
        );

        AiServiceException exception = assertThrows(
                AiServiceException.class,
                () -> client.analyzeRepository(context())
        );

        assertTrue(exception.getMessage().contains("HTTP 502"));
        assertTrue(exception.getMessage().contains("Model output failed validation"));
    }

    private RepositorySnapshotContext context() {
        return new RepositorySnapshotContext(
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
    }
}
