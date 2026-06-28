package com.mesha.api.service;

import com.mesha.api.config.LocalAiCatalogProperties;
import com.mesha.api.dto.LocalAiModelDto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAiModelDownloadProxyServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LocalAiModelDto modelPointingAt(String path, String source) {
        return new LocalAiModelDto(
                "gemma-3n-e2b", "Gemma 3n E2B", "Google", source, "1.0", "mediapipe",
                "model.task", 1024L, "", "http://localhost:" + server.getAddress().getPort() + path,
                null, 6, 5, true);
    }

    @Test
    void forwardsBearerTokenForHuggingFaceSources() throws Exception {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/model.task", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payload = "model-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setHuggingFaceToken("secret-token");
        LocalAiModelDownloadProxyService service = new LocalAiModelDownloadProxyService(props);

        ResponseEntity<StreamingResponseBody> response = service.proxy(modelPointingAt("/model.task", "huggingface"), null);

        assertThat(receivedAuth.get()).isEqualTo("Bearer secret-token");
        assertThat(drain(response)).isEqualTo("model-bytes");
    }

    @Test
    void omitsAuthorizationForNonHuggingFaceSources() throws Exception {
        AtomicReference<String> receivedAuth = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/model.task", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setHuggingFaceToken("secret-token");
        LocalAiModelDownloadProxyService service = new LocalAiModelDownloadProxyService(props);

        service.proxy(modelPointingAt("/model.task", "mesha-cdn"), null);

        assertThat(receivedAuth.get()).isNull();
    }

    @Test
    void surfacesUpstreamErrorStatusAsResponseStatusException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/model.task", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        LocalAiModelDownloadProxyService service = new LocalAiModelDownloadProxyService(props);

        assertThatThrownBy(() -> service.proxy(modelPointingAt("/model.task", "huggingface"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    private String drain(ResponseEntity<StreamingResponseBody> response) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
