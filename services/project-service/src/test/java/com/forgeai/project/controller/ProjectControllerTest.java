package com.forgeai.project.controller;

import java.util.List;

import com.forgeai.project.exception.ProjectNotFoundException;
import com.forgeai.project.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.forgeai.project.dto.CreateProjectRequest;
import com.forgeai.project.dto.ProjectResponse;
import com.forgeai.project.entity.Project;
import com.forgeai.project.mapper.ProjectMapper;
import com.forgeai.project.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.forgeai.project.security.JwtAuthenticationFilter;

@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ProjectMapper projectMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void createProject_shouldReturnCreatedProject() throws Exception {

        Project project = new Project();
        project.setId(5L);
        project.setUserId(1L);
        project.setName("ForgeAI");
        project.setDescription("AI coding platform");

        ProjectResponse response = new ProjectResponse();
        response.setId(5L);
        response.setUserId(1L);
        response.setName("ForgeAI");
        response.setDescription("AI coding platform");

        when(projectService.createProject(
                any(CreateProjectRequest.class),
                any(Long.class)
        )).thenReturn(project);

        when(projectMapper.toResponse(project))
                .thenReturn(response);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                1L,
                "developer@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        mockMvc.perform(
                        post("/api/v1/projects")
                                .contentType("application/json")
                                .content("""
                                {
                                  "userId": 1,
                                  "name": "ForgeAI",
                                  "description": "AI coding platform"
                                }
                                """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("ForgeAI"))
                .andExpect(jsonPath("$.description").value("AI coding platform"));
    }
    @Test
    void getProject_shouldReturnProject() throws Exception {

        Project project = new Project();
        project.setId(5L);
        project.setUserId(1L);
        project.setName("ForgeAI");
        project.setDescription("AI coding platform");

        ProjectResponse response = new ProjectResponse();
        response.setId(5L);
        response.setUserId(1L);
        response.setName("ForgeAI");
        response.setDescription("AI coding platform");

        when(projectService.getProject(5L,1L))
                .thenReturn(project);

        when(projectMapper.toResponse(project))
                .thenReturn(response);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                1L,
                "developer@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        mockMvc.perform(
                        get("/api/v1/projects/5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("ForgeAI"))
                .andExpect(jsonPath("$.description").value("AI coding platform"));
    }
    @Test
    void getProjects_shouldReturnUserProjects() throws Exception {

        Project project1 = new Project();
        project1.setId(5L);
        project1.setUserId(1L);
        project1.setName("ForgeAI");
        project1.setDescription("AI coding platform");

        Project project2 = new Project();
        project2.setId(6L);
        project2.setUserId(1L);
        project2.setName("Website");
        project2.setDescription("Website project");

        ProjectResponse response1 = new ProjectResponse();
        response1.setId(5L);
        response1.setUserId(1L);
        response1.setName("ForgeAI");
        response1.setDescription("AI coding platform");

        ProjectResponse response2 = new ProjectResponse();
        response2.setId(6L);
        response2.setUserId(1L);
        response2.setName("Website");
        response2.setDescription("Website project");

        when(projectService.getProjectsForUser(1L))
                .thenReturn(List.of(project1, project2));

        when(projectMapper.toResponse(project1))
                .thenReturn(response1);

        when(projectMapper.toResponse(project2))
                .thenReturn(response2);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                1L,
                "developer@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        mockMvc.perform(
                        get("/api/v1/projects")
                                .param("userId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("ForgeAI"))
                .andExpect(jsonPath("$[1].id").value(6))
                .andExpect(jsonPath("$[1].name").value("Website"));
    }
    @Test
    void deleteProject_shouldReturnNoContent() throws Exception {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                2L,
                "developer2@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        doNothing().when(projectService).deleteProject(6L, 2L);

        mockMvc.perform(
                        delete("/api/v1/projects/6")
                )
                .andExpect(status().isNoContent());
        verify(projectService).deleteProject(6L, 2L);
    }
    @Test
    void connectGitHubRepository_shouldReturnUpdatedProject() throws Exception {

        Project project = new Project();
        project.setId(5L);
        project.setUserId(2L);
        project.setName("User2 Project");

        ProjectResponse response = new ProjectResponse();
        response.setId(5L);
        response.setUserId(2L);
        response.setName("User2 Project");
        response.setGithubOwner("octocat");
        response.setGithubRepoName("Hello-World");
        response.setGithubRepoUrl(
                "https://github.com/octocat/Hello-World"
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                2L,
                "developer2@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        when(projectService.connectGitHubRepository(
                5L,
                2L,
                "octocat",
                "Hello-World"
        )).thenReturn(project);

        when(projectMapper.toResponse(project))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/projects/5/github")
                                .contentType("application/json")
                                .content("""
                            {
                              "owner": "octocat",
                              "repository": "Hello-World"
                            }
                            """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.githubOwner").value("octocat"))
                .andExpect(jsonPath("$.githubRepoName").value("Hello-World"))
                .andExpect(jsonPath("$.githubRepoUrl")
                        .value("https://github.com/octocat/Hello-World"));
    }
    @Test
    void connectGitHubRepository_shouldRejectProjectOwnedByAnotherUser()
            throws Exception {

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                2L,
                "developer2@forgeai.local",
                "DEVELOPER"
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        List.of()
                )
        );

        when(projectService.connectGitHubRepository(
                3L,
                2L,
                "octocat",
                "Hello-World"
        )).thenThrow(new ProjectNotFoundException(3L));

        mockMvc.perform(
                        post("/api/v1/projects/3/github")
                                .contentType("application/json")
                                .content("""
                            {
                              "owner": "octocat",
                              "repository": "Hello-World"
                            }
                            """)
                )
                .andExpect(status().isNotFound());
    }
}