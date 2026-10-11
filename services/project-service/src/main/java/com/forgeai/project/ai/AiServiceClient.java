package com.forgeai.project.ai;

import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiServiceClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper
    ) {
        this.restClient = builder
                .baseUrl("http://localhost:8001")
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();

        this.objectMapper = objectMapper;
    }

    // Used by tests so MockRestServiceServer can control the HTTP transport.
    AiServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public RepositoryAnalysisResponse analyzeRepository(
            RepositorySnapshotContext context
    ) {
        try {
            String requestJson =
                    objectMapper.writeValueAsString(context);

            return restClient.post()
                    .uri("/api/v1/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestJson.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .body(RepositoryAnalysisResponse.class);

        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize repository analysis request",
                    e
            );
        }
    }
}