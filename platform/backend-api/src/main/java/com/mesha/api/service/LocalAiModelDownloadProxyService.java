package com.mesha.api.service;

import com.mesha.api.config.LocalAiCatalogProperties;
import com.mesha.api.dto.LocalAiModelDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Streams Local AI model artifacts through the backend instead of letting the mobile client
 * fetch them directly.
 *
 * <p>Google's litert-preview Gemma repos on Hugging Face are gated: an anonymous request to
 * the {@code resolve} URL returns 401. Mobile clients can't reasonably hold a personal
 * Hugging Face credential, so the backend authenticates with a service-account token
 * ({@link LocalAiCatalogProperties#getHuggingFaceToken()}) configured by the operator and
 * relays the bytes. Sources other than {@code huggingface} are proxied without an
 * Authorization header.
 */
@Service
public class LocalAiModelDownloadProxyService {

    private static final Logger log = LoggerFactory.getLogger(LocalAiModelDownloadProxyService.class);
    private static final String HUGGING_FACE_SOURCE = "huggingface";

    private final LocalAiCatalogProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LocalAiModelDownloadProxyService(LocalAiCatalogProperties properties) {
        this.properties = properties;
    }

    /** Proxies [model]'s artifact, forwarding the client's Range header for resumable downloads. */
    public ResponseEntity<StreamingResponseBody> proxy(LocalAiModelDto model, String rangeHeader) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(model.downloadUrl())).GET();
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            requestBuilder.header(HttpHeaders.RANGE, rangeHeader);
        }
        String token = properties.getHuggingFaceToken();
        if (HUGGING_FACE_SOURCE.equals(model.source()) && token != null && !token.isBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        HttpResponse<InputStream> upstream;
        try {
            upstream = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("Local AI model download proxy failed modelId={}", model.id(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach model host");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Local AI model download proxy interrupted modelId={}", model.id(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Download interrupted");
        }

        int status = upstream.statusCode();
        if (status != 200 && status != 206) {
            closeQuietly(upstream.body());
            log.warn("Local AI model host returned HTTP {} modelId={}", status, model.id());
            throw new ResponseStatusException(HttpStatusCode.valueOf(status), "Model host returned HTTP " + status);
        }

        StreamingResponseBody body = output -> {
            try (InputStream in = upstream.body()) {
                in.transferTo(output);
            } catch (IOException e) {
                if (isClientDisconnect(e)) {
                    log.info("Local AI model download client disconnected modelId={}", model.id());
                } else {
                    log.warn("Local AI model download proxy stream interrupted modelId={}", model.id(), e);
                }
                throw e;
            }
        };

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        upstream.headers().firstValue(HttpHeaders.CONTENT_LENGTH).ifPresent(v -> responseBuilder.header(HttpHeaders.CONTENT_LENGTH, v));
        upstream.headers().firstValue(HttpHeaders.CONTENT_RANGE).ifPresent(v -> responseBuilder.header(HttpHeaders.CONTENT_RANGE, v));
        upstream.headers().firstValue(HttpHeaders.ACCEPT_RANGES).ifPresent(v -> responseBuilder.header(HttpHeaders.ACCEPT_RANGES, v));
        return responseBuilder.body(body);
    }

    /**
     * Resolves [model]'s actual fetch location without transferring its body, so the mobile
     * client can download large artifacts directly from the source instead of relaying every
     * byte through this backend. For gated Hugging Face repos, the {@code resolve/main} URL
     * 302s to a time-limited signed CDN URL once authenticated; a {@code Range: bytes=0-0}
     * request follows that redirect chain while transferring at most one byte, and
     * {@link HttpResponse#uri()} reports the URI the response actually came from — the signed
     * CDN link. If Hugging Face doesn't redirect (no CDN hop for this artifact), the returned
     * URL is simply the original gated URL unchanged; that's expected, not an error, and the
     * mobile client falls back to the full proxy endpoint when a direct fetch fails. Sources
     * other than Hugging Face already hand mobile a directly-fetchable URL, so there's nothing
     * to resolve and no upstream request is made.
     */
    public String resolveDownloadUrl(LocalAiModelDto model) {
        if (!HUGGING_FACE_SOURCE.equals(model.source())) {
            return model.downloadUrl();
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(model.downloadUrl()))
                .header(HttpHeaders.RANGE, "bytes=0-0")
                .GET();
        String token = properties.getHuggingFaceToken();
        if (token != null && !token.isBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        HttpResponse<Void> upstream;
        try {
            upstream = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            log.warn("Local AI model resolve failed modelId={}", model.id(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach model host");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Local AI model resolve interrupted modelId={}", model.id(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resolve interrupted");
        }

        int status = upstream.statusCode();
        if (status != 200 && status != 206) {
            log.warn("Local AI model host returned HTTP {} during resolve modelId={}", status, model.id());
            throw new ResponseStatusException(HttpStatusCode.valueOf(status), "Model host returned HTTP " + status);
        }
        return upstream.uri().toString();
    }

    private void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // Best-effort cleanup of a stream we're discarding anyway.
        }
    }

    private boolean isClientDisconnect(IOException e) {
        if (e.getClass().getSimpleName().equals("ClientAbortException")) {
            return true;
        }
        String message = e.getMessage();
        return message != null && (message.contains("Broken pipe") || message.contains("Connection reset"));
    }
}
