package com.forgeai.project.ai;

import com.forgeai.project.dto.AiGenerateRequest;
import com.forgeai.project.dto.AiPlanRequest;
import com.forgeai.project.dto.AiSolveRequest;
import com.forgeai.project.dto.EngineeringPlanResponse;
import com.forgeai.project.dto.GenerationResultResponse;
import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.forgeai.project.dto.SolveResponse;
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

    static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    // Same service, longer read timeout: a solve call runs several model
    // calls and sandbox attempts before it answers.
    private final RestClient solveRestClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiServiceClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${ai-service.base-url}") String baseUrl,
            @Value("${ai-service.connect-timeout}") Duration connectTimeout,
            @Value("${ai-service.read-timeout}") Duration readTimeout,
            @Value("${ai-service.solve-read-timeout}") Duration solveReadTimeout,
            @Value("${ai-service.token:}") String token
    ) {
        builder.baseUrl(baseUrl);
        // Deployed, the AI service is reachable from the internet, so it only
        // answers callers that present this shared secret. Blank locally.
        if (!token.isBlank()) {
            builder.defaultHeader(SERVICE_TOKEN_HEADER, token);
        }

        // A local model can take minutes per call, so the read timeouts are long
        // and explicit rather than the JDK default of "wait forever".
        this.restClient = builder
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();

        this.solveRestClient = builder
                .requestFactory(requestFactory(connectTimeout, solveReadTimeout))
                .build();

        this.objectMapper = objectMapper;
    }

    // Used by tests so MockRestServiceServer can control the HTTP transport.
    AiServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.solveRestClient = restClient;
        this.objectMapper = objectMapper;
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            Duration connectTimeout,
            Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    // Starts the AI service if its host has put it to sleep, without waiting
    // for it: the caller is a page load and should not be held up.
    public void wakeUp() {
        Thread.ofVirtual().name("wake-ai-service").start(() -> {
            try {
                restClient.get().uri("/health").retrieve().toBodilessEntity();
            } catch (RuntimeException ignored) {
                // Still asleep or unreachable: the next real request will say so.
            }
        });
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

    public EngineeringPlanResponse createPlan(
            AiPlanRequest request
    ) {
        return post(
                "/api/v1/plan",
                request,
                EngineeringPlanResponse.class
        );
    }

    public GenerationResultResponse generateChanges(
            AiGenerateRequest request
    ) {
        return post(
                "/api/v1/generate",
                request,
                GenerationResultResponse.class
        );
    }

    // Generate, test in the sandbox and self-correct. Can take many minutes.
    public SolveResponse solve(
            AiSolveRequest request
    ) {
        return post(
                solveRestClient,
                "/api/v1/solve",
                request,
                SolveResponse.class
        );
    }

    private <T> T post(
            String path,
            Object request,
            Class<T> responseType
    ) {
        return post(restClient, path, request, responseType);
    }

    private <T> T post(
            RestClient client,
            String path,
            Object request,
            Class<T> responseType
    ) {
        byte[] requestBody = serialize(request);

        try {
            // The explicit byte body is deliberate: it guarantees the JSON is
            // actually written to the wire (an earlier streaming body arrived empty).
            return client.post()
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
