package com.forgeai.project.ai;

import com.forgeai.project.dto.RepositoryAnalysisResponse;
import com.forgeai.project.dto.RepositoryMetadata;
import com.forgeai.project.dto.RepositorySnapshotContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceClientTokenTest {

    private HttpServer server;
    private final AtomicReference<String> receivedToken = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiServiceClient client(String token) {
        return new AiServiceClient(
                RestClient.builder(),
                new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                token
        );
    }

    private static RepositorySnapshotContext context() {
        return new RepositorySnapshotContext(
                "octocat",
                "demo",
                List.of(),
                new RepositoryMetadata("Python", false)
        );
    }

    @Test
    void sendsTheSharedSecretWhenOneIsConfigured() {
        RepositoryAnalysisResponse response = client("s3cret").analyzeRepository(context());

        assertThat(response).isNotNull();
        assertThat(receivedToken.get()).isEqualTo("s3cret");
    }

    @Test
    void sendsNoTokenHeaderWhenNoneIsConfigured() {
        client("").analyzeRepository(context());

        assertThat(receivedToken.get()).isNull();
    }
}
