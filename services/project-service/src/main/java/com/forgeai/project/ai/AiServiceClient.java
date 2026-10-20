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
import java.util.List;
import java.util.Set;

@Component
public class AiServiceClient {

    static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    // A host such as Render answers with its own error page, not the service's
    // JSON, while the service is restarting or not yet listening. Such a request
    // never got an answer from the service, so it is sent again after a pause.
    private static final Set<Integer> HOST_FAILURES = Set.of(502, 503, 504);
    private static final List<Duration> HOST_RETRY_DELAYS =
            List.of(Duration.ofSeconds(5), Duration.ofSeconds(20));

    // Longer messages are cut: they end up on the run's page.
    private static final int MAX_DETAIL_LENGTH = 400;

    private final RestClient restClient;
    // Same service, longer read timeout: a solve call runs several model
    // calls and sandbox attempts before it answers.
    private final RestClient solveRestClient;
    private final ObjectMapper objectMapper;
    private final List<Duration> hostRetryDelays;

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

        // A solve call makes many model calls and sandbox runs, so the read
        // timeouts are long, and explicit rather than the JDK default of "wait forever".
        this.restClient = builder
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();

        this.solveRestClient = builder
                .requestFactory(requestFactory(connectTimeout, solveReadTimeout))
                .build();

        this.objectMapper = objectMapper;
        this.hostRetryDelays = HOST_RETRY_DELAYS;
    }

    // Used by tests so MockRestServiceServer can control the HTTP transport.
    AiServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this(restClient, objectMapper, List.of(Duration.ZERO, Duration.ZERO));
    }

    AiServiceClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            List<Duration> hostRetryDelays
    ) {
        this.restClient = restClient;
        this.solveRestClient = restClient;
        this.objectMapper = objectMapper;
        this.hostRetryDelays = hostRetryDelays;
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

        for (int attempt = 0; ; attempt++) {
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
                String detail = serviceDetail(e);
                boolean hostFailure = detail == null
                        && HOST_FAILURES.contains(e.getStatusCode().value());
                if (hostFailure && attempt < hostRetryDelays.size()) {
                    pause(hostRetryDelays.get(attempt));
                    continue;
                }
                throw new AiServiceException(
                        "AI service returned HTTP "
                                + e.getStatusCode().value()
                                + ": "
                                + (detail != null ? detail : hostDetail(e)),
                        e
                );
            } catch (ResourceAccessException e) {
                throw new AiServiceException(
                        "AI service is unreachable or timed out",
                        e
                );
            }
        }
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("Interrupted while waiting to retry", interrupted);
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

    // The AI service reports its own errors as {"detail": ...}. Null means the
    // answer came from somewhere else, such as the host's error page.
    private String serviceDetail(RestClientResponseException e) {
        try {
            JsonNode detail = objectMapper.readTree(e.getResponseBodyAsString()).path("detail");
            if (detail.isMissingNode()) {
                return null;
            }
            return shorten(detail.isString() ? detail.asString() : detail.toString());
        } catch (JacksonException notJson) {
            return null;
        }
    }

    private static String hostDetail(RestClientResponseException e) {
        String body = e.getResponseBodyAsString().strip();
        if (body.startsWith("<")) {
            return "the AI service's host answered with an error page instead of the service."
                    + " The service was probably restarting or starting up; try again.";
        }
        return body.isEmpty() ? "no details were given" : shorten(body);
    }

    private static String shorten(String text) {
        return text.length() <= MAX_DETAIL_LENGTH
                ? text
                : text.substring(0, MAX_DETAIL_LENGTH) + "...";
    }
}
