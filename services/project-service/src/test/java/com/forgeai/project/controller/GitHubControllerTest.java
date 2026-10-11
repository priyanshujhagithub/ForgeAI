package com.forgeai.project.controller;

import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.exception.AiServiceException;
import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.security.AuthenticatedUser;
import com.forgeai.project.security.JwtAuthenticationFilter;
import com.forgeai.project.service.AiAnalysisService;
import com.forgeai.project.service.AiTaskService;
import com.forgeai.project.service.GitHubConnectionService;
import com.forgeai.project.service.GitHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GitHubController.class)
@AutoConfigureMockMvc(addFilters = false)
class GitHubControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubService gitHubService;

    @MockitoBean
    private GitHubConnectionService gitHubConnectionService;

    @MockitoBean
    private AiAnalysisService aiAnalysisService;

    @MockitoBean
    private AiTaskService aiTaskService;

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
    void planTask_shouldReturnPlanForTheAuthenticatedUsersProject() throws Exception {
        EngineeringPlanResponse plan = new EngineeringPlanResponse();
        plan.setTaskUnderstanding("Validate registration input.");
        plan.setFilesToModify(List.of("app/models.py"));
        plan.setFilesToCreate(List.of());
        plan.setSteps(List.of());

        when(aiTaskService.createPlan(2L, 5L, "Add input validation"))
                .thenReturn(plan);

        mockMvc.perform(
                        post("/api/v1/github/projects/5/plan")
                                .contentType("application/json")
                                .content("""
                                        {"task": "Add input validation"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskUnderstanding")
                        .value("Validate registration input."))
                .andExpect(jsonPath("$.filesToModify[0]").value("app/models.py"));
    }

    @Test
    void planTask_shouldRejectBlankTaskWithoutCallingTheAiService() throws Exception {
        mockMvc.perform(
                        post("/api/v1/github/projects/5/plan")
                                .contentType("application/json")
                                .content("""
                                        {"task": "   "}
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(aiTaskService);
    }

    @Test
    void planTask_shouldReturnNotFoundForAnotherUsersProject() throws Exception {
        when(aiTaskService.createPlan(any(), any(), anyString()))
                .thenThrow(new ProjectNotFoundException(5L));

        mockMvc.perform(
                        post("/api/v1/github/projects/5/plan")
                                .contentType("application/json")
                                .content("""
                                        {"task": "Add input validation"}
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("Project not found with id: 5"));
    }

    @Test
    void planTask_shouldReturnBadGatewayWhenTheAiServiceFails() throws Exception {
        when(aiTaskService.createPlan(any(), any(), anyString()))
                .thenThrow(new AiServiceException(
                        "AI service returned HTTP 502: Model output failed validation",
                        null
                ));

        mockMvc.perform(
                        post("/api/v1/github/projects/5/plan")
                                .contentType("application/json")
                                .content("""
                                        {"task": "Add input validation"}
                                        """)
                )
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail")
                        .value("AI service returned HTTP 502: Model output failed validation"));
    }
}
