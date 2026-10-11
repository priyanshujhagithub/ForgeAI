package com.forgeai.project.controller;

import com.forgeai.project.dto.GitHubTokenResponse;
import com.forgeai.project.dto.GitHubUserResponse;
import com.forgeai.project.github.GitHubClient;
import com.forgeai.project.github.GitHubOAuthService;
import com.forgeai.project.github.GitHubOAuthStateService;
import com.forgeai.project.mapper.UserMapper;
import com.forgeai.project.security.JwtAuthenticationFilter;
import com.forgeai.project.service.GitHubConnectionService;
import com.forgeai.project.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private GitHubOAuthService gitHubOAuthService;
    @MockitoBean
    private GitHubClient gitHubClient;
    @MockitoBean
    private GitHubOAuthStateService gitHubOAuthStateService;
    @MockitoBean
    private GitHubConnectionService gitHubConnectionService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void callback_shouldStoreTheConnectionAndReturnTheBrowserToTheWebApp() throws Exception {
        GitHubTokenResponse token = new GitHubTokenResponse();
        token.setAccessToken("gho_token");

        GitHubUserResponse githubUser = new GitHubUserResponse();
        githubUser.setId(42L);
        githubUser.setLogin("octocat");

        when(gitHubOAuthStateService.consumeState("good-state")).thenReturn(3L);
        when(gitHubOAuthService.exchangeCodeForAccessToken("the-code")).thenReturn(token);
        when(gitHubClient.getAuthenticatedUser("gho_token")).thenReturn(githubUser);

        mockMvc.perform(
                        get("/api/v1/auth/github/callback")
                                .param("code", "the-code")
                                .param("state", "good-state")
                )
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5180/app/settings?github=connected"
                ));

        // The connection is saved for the user who started the flow, not for
        // anyone named in the request.
        verify(gitHubConnectionService).saveConnection(3L, 42L, "octocat", "gho_token");
    }

    @Test
    void callback_shouldStoreNothingForAnUnknownOrReusedState() throws Exception {
        when(gitHubOAuthStateService.consumeState("stale-state")).thenReturn(null);

        mockMvc.perform(
                        get("/api/v1/auth/github/callback")
                                .param("code", "the-code")
                                .param("state", "stale-state")
                )
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5180/app/settings?github=error"
                ));

        verifyNoInteractions(gitHubOAuthService, gitHubClient, gitHubConnectionService);
    }
}
