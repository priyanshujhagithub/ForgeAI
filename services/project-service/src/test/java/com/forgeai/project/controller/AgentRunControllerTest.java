package com.forgeai.project.controller;

import com.forgeai.project.entity.AgentRun;
import com.forgeai.project.entity.AgentRunStatus;
import com.forgeai.project.exception.AgentRunAlreadyActiveException;
import com.forgeai.project.exception.AgentRunNotFoundException;
import com.forgeai.project.mapper.AgentRunMapper;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.security.JwtAuthenticationFilter;
import com.forgeai.project.service.AgentRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentRunController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AgentRunMapper.class)
class AgentRunControllerTest {

    private static final UUID RUN_ID =
            UUID.fromString("1234abcd-0000-4000-8000-000000000000");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRunService agentRunService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void authenticateAsUserTwo() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(
                                2L,
                                "developer2@forgeai.local",
                                "DEVELOPER"
                        ),
                        null,
                        List.of()
                )
        );
    }

    @Test
    void startRun_shouldAnswer202WithWhereToPoll() throws Exception {
        when(agentRunService.start(2L, 5L, "Add input validation"))
                .thenReturn(run(AgentRunStatus.QUEUED));

        mockMvc.perform(
                        post("/api/v1/projects/5/runs")
                                .contentType("application/json")
                                .content("""
                                        {"task": "Add input validation"}
                                        """)
                )
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/runs/" + RUN_ID))
                .andExpect(jsonPath("$.id").value(RUN_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.plan").doesNotExist());
    }

    @Test
    void startRun_shouldRejectABlankTask() throws Exception {
        mockMvc.perform(
                        post("/api/v1/projects/5/runs")
                                .contentType("application/json")
                                .content("""
                                        {"task": ""}
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(agentRunService);
    }

    @Test
    void startRun_shouldAnswer409WhenARunIsAlreadyInProgress() throws Exception {
        when(agentRunService.start(2L, 5L, "Add input validation"))
                .thenThrow(new AgentRunAlreadyActiveException(5L));

        mockMvc.perform(
                        post("/api/v1/projects/5/runs")
                                .contentType("application/json")
                                .content("""
                                        {"task": "Add input validation"}
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Project 5 already has a run in progress"));
    }

    @Test
    void getRun_shouldReturnStoredResultsAsStructuredJson() throws Exception {
        AgentRun run = run(AgentRunStatus.COMPLETED);
        run.setPlanJson("""
                {"taskUnderstanding": "Validate input.", "filesToModify": ["app/models.py"],
                 "filesToCreate": [], "steps": []}
                """);
        run.setResultJson("""
                {"outcome": "passed", "finalChanges": [], "attempts": [],
                 "sandboxSeconds": 13.5, "estimatedCostUsd": 0.0004}
                """);
        run.setPullRequestUrl("https://github.com/octocat/demo/pull/7");
        run.setSandboxSeconds(13.5);

        when(agentRunService.get(2L, RUN_ID)).thenReturn(run);

        mockMvc.perform(get("/api/v1/runs/" + RUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.plan.filesToModify[0]").value("app/models.py"))
                .andExpect(jsonPath("$.result.outcome").value("passed"))
                .andExpect(jsonPath("$.pullRequestUrl")
                        .value("https://github.com/octocat/demo/pull/7"))
                .andExpect(jsonPath("$.sandboxSeconds").value(13.5));
    }

    @Test
    void getRun_shouldAnswer404ForAnotherUsersRun() throws Exception {
        when(agentRunService.get(2L, RUN_ID))
                .thenThrow(new AgentRunNotFoundException(RUN_ID));

        mockMvc.perform(get("/api/v1/runs/" + RUN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRuns_shouldReturnTheProjectsRuns() throws Exception {
        when(agentRunService.listForProject(2L, 5L))
                .thenReturn(List.of(run(AgentRunStatus.TESTS_FAILED)));

        mockMvc.perform(get("/api/v1/projects/5/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("TESTS_FAILED"));
    }

    private static AgentRun run(AgentRunStatus status) {
        AgentRun run = new AgentRun();
        run.setId(RUN_ID);
        run.setProjectId(5L);
        run.setUserId(2L);
        run.setTask("Add input validation");
        run.setStatus(status);
        return run;
    }
}
