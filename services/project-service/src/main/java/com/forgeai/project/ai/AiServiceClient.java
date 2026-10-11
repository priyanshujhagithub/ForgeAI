package com.forgeai.project.ai;

import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.exception.AiServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiServiceClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${ai-service.base-url}") String baseUrl,
            @Value("${ai-service.connect-timeout}") Duration connectTimeout,
            @Value("${ai-service.read-timeout}") Duration readTimeout
    ) {
        // A local model can take minutes per call, so the read timeout is long
        // and explicit rather than the JDK default of "wait forever".
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
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
        return post(
                "/api/v1/analyze",
                context,
                RepositoryAnalysisResponse.class
        );
    }

    private <T> T post(
            String path,
            Object request,
            Class<T> responseType
    ) {
        byte[] requestBody = serialize(request);

        try {
            // The explicit byte body is deliberate: it guarantees the JSON is
            // actually written to the wire (an earlier streaming body arrived empty).
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(responseType);

        } catch (RestClientResponseException e) {
            throw new AiServiceException(
                    "AI service returned HTTP "
                            + e.getStatusCode().value()
                            + ": "
                            + errorDetail(e),
                    e
            );
        } catch (ResourceAccessException e) {
            throw new AiServiceException(
                    "AI service is unreachable or timed out",
                    e
            );
        }
    }

    private byte[] serialize(Object request) {
        try {
            return objectMapper
                    .writeValueAsString(request)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to serialize AI service request",
                    e
            );
        }
    }

    // FastAPI reports errors as {"detail": ...}.
    private String errorDetail(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        try {
            JsonNode detail = objectMapper.readTree(body).path("detail");
            return detail.isString() ? detail.asString() : detail.toString();
        } catch (JacksonException parseFailure) {
            return body;
        }
    }
}
