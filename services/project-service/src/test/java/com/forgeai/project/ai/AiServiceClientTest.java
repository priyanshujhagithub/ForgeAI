package com.forgeai.project.ai;

import com.forgeai.project.dto.AiGenerateRequest;
import com.forgeai.project.dto.AiPlanRequest;
import com.forgeai.project.dto.AiRepositoryRef;
import com.forgeai.project.dto.AiSolveRequest;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.GenerationResultResponse;
import com.forgeai.project.dto.GitHubFileResponse;
import com.forgeai.project.dto.LoopResultResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.dto.SolveResponse;
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

    @Test
    void shouldSendTaskWithContextAndReadEngineeringPlan() {
        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/plan"
                )
        ).andExpect(
                MockRestRequestMatchers.method(HttpMethod.POST)
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.task")
                        .value("Add input validation")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.context.repository")
                        .value("Hello-World")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.context.files[0].path")
                        .value("README")
        ).andRespond(
                MockRestResponseCreators.withSuccess(
                        """
                        {
                          "taskUnderstanding": "Validate registration input.",
                          "filesToModify": ["README"],
                          "filesToCreate": ["tests/test_readme.py"],
                          "steps": [
                            {
                              "order": 1,
                              "description": "Document the rules.",
                              "files": ["README"]
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                )
        );

        EngineeringPlanResponse plan = client.createPlan(
                new AiPlanRequest(context(), "Add input validation", null)
        );

        assertEquals("Validate registration input.", plan.getTaskUnderstanding());
        assertEquals(List.of("README"), plan.getFilesToModify());
        assertEquals(List.of("tests/test_readme.py"), plan.getFilesToCreate());
        assertEquals(1, plan.getSteps().size());
        assertEquals(1, plan.getSteps().get(0).getOrder());
        assertEquals("Document the rules.", plan.getSteps().get(0).getDescription());
        assertEquals(List.of("README"), plan.getSteps().get(0).getFiles());

        server.verify();
    }

    @Test
    void shouldSendPlanAndReadGeneratedChanges() {
        EngineeringPlanResponse plan = new EngineeringPlanResponse();
        plan.setTaskUnderstanding("Validate registration input.");
        plan.setFilesToModify(List.of("README"));
        plan.setFilesToCreate(List.of());
        plan.setSteps(List.of());

        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/generate"
                )
        ).andExpect(
                MockRestRequestMatchers.method(HttpMethod.POST)
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.task")
                        .value("Add input validation")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.plan.filesToModify[0]")
                        .value("README")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.context.owner")
                        .value("octocat")
        ).andRespond(
                MockRestResponseCreators.withSuccess(
                        """
                        {
                          "changes": [
                            {
                              "path": "README",
                              "action": "modify",
                              "content": "hello, validated\\n",
                              "explanation": "Document the rules."
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON
                )
        );

        GenerationResultResponse result = client.generateChanges(
                new AiGenerateRequest(context(), "Add input validation", plan)
        );

        assertEquals(1, result.getChanges().size());
        assertEquals("README", result.getChanges().get(0).getPath());
        assertEquals("modify", result.getChanges().get(0).getAction());
        assertEquals("hello, validated\n", result.getChanges().get(0).getContent());
        assertEquals("Document the rules.", result.getChanges().get(0).getExplanation());

        server.verify();
    }

    @Test
    void shouldSendRepositoryRefAndReadTheLoopResult() {
        server.expect(
                MockRestRequestMatchers.requestTo(
                        "http://localhost:8001/api/v1/solve"
                )
        ).andExpect(
                MockRestRequestMatchers.method(HttpMethod.POST)
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.repository.cloneUrl")
                        .value("https://github.com/octocat/Hello-World.git")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.repository.baseSha")
                        .value("base-sha")
        ).andExpect(
                MockRestRequestMatchers.jsonPath("$.task")
                        .value("Add input validation")
        ).andRespond(
                MockRestResponseCreators.withSuccess(
                        """
                        {
                          "plan": {
                            "taskUnderstanding": "Validate input.",
                            "filesToModify": ["README"],
                            "filesToCreate": [],
                            "steps": []
                          },
                          "result": {
                            "outcome": "passed",
                            "finalChanges": [
                              {"path": "README", "action": "modify",
                               "content": "hello\\n", "explanation": "Docs."}
                            ],
                            "attempts": [
                              {
                                "number": 1,
                                "diagnosis": null,
                                "changes": [],
                                "sandboxResult": {
                                  "outcome": "tests_failed",
                                  "command": "python -m pytest -q",
                                  "exitCode": 1,
                                  "stdout": "1 failed",
                                  "stderr": "",
                                  "durationSeconds": 1.2,
                                  "timedOut": false,
                                  "message": null
                                }
                              },
                              {
                                "number": 2,
                                "diagnosis": {
                                  "rootCause": "Wrong import.",
                                  "fixStrategy": "Fix the import.",
                                  "filesToChange": ["README"]
                                },
                                "changes": [],
                                "sandboxResult": {
                                  "outcome": "passed",
                                  "command": "python -m pytest -q",
                                  "exitCode": 0,
                                  "stdout": "3 passed",
                                  "stderr": "",
                                  "durationSeconds": 1.0,
                                  "timedOut": false,
                                  "message": null
                                }
                              }
                            ],
                            "sandboxSeconds": 27.4,
                            "estimatedCostUsd": 0.00082,
                            "message": null
                          }
                        }
                        """,
                        MediaType.APPLICATION_JSON
                )
        );

        SolveResponse response = client.solve(
                new AiSolveRequest(
                        context(),
                        "Add input validation",
                        new AiRepositoryRef(
                                "https://github.com/octocat/Hello-World.git",
                                "master",
                                "base-sha",
                                null
                        ),
                        null,
                        null
                )
        );

        LoopResultResponse result = response.getResult();
        assertEquals("passed", result.getOutcome());
        assertEquals("README", result.getFinalChanges().get(0).getPath());
        assertEquals(2, result.getAttempts().size());
        assertEquals(1, result.getAttempts().get(0).getSandboxResult().getExitCode());
        assertEquals("Wrong import.", result.getAttempts().get(1).getDiagnosis().getRootCause());
        assertEquals(27.4, result.getSandboxSeconds());
        assertEquals(0.00082, result.getEstimatedCostUsd());
        assertEquals(List.of("README"), response.getPlan().getFilesToModify());

        server.verify();
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
