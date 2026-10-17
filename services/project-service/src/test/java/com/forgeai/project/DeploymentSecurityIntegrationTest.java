package com.forgeai.project;

import com.forgeai.project.ai.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The parts of the security setup that matter once the web app and the API
// live at different addresses, checked through the real filter chain.

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DeploymentSecurityIntegrationTest {

    // forgeai.frontend-url in the test properties
    private static final String WEB_APP = "http://localhost:5180";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @Test
    void healthAndWakeNeedNoSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mockMvc.perform(get("/api/v1/wake"))
                .andExpect(status().isOk());
        verify(aiServiceClient).wakeUp();
    }

    @Test
    void everythingElseStillNeedsSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theWebAppMayCallTheApiFromItsOwnAddress() throws Exception {
        mockMvc.perform(options("/api/v1/projects")
                        .header("Origin", WEB_APP)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", WEB_APP));
    }

    @Test
    void anyOtherSiteMayNot() throws Exception {
        mockMvc.perform(options("/api/v1/projects")
                        .header("Origin", "https://elsewhere.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
