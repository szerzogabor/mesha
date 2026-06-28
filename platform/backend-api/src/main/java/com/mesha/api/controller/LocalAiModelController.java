package com.mesha.api.controller;

import com.mesha.api.dto.LocalAiModelDto;
import com.mesha.api.service.LocalAiCatalogService;
import com.mesha.api.service.LocalAiModelDownloadProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Local AI model catalog endpoints.
 *
 * <p>Read-only and public (registered as {@code permitAll()} in {@code SecurityConfig}):
 * the catalog is non-sensitive reference data the mobile app needs to discover, download
 * and manage on-device models. The backend is the source of truth — clients must never
 * hardcode model download URLs.
 *
 * <p>For {@code huggingface}-sourced entries, the catalog's {@code downloadUrl} is rewritten
 * to point at this controller's own {@code /download} endpoint rather than the upstream URL
 * directly: several of the curated repos (e.g. the Gemma 3n litert-preview models) are
 * gated and 401 on anonymous requests, so the backend fetches them with a service-account
 * credential and relays the bytes — see {@link LocalAiModelDownloadProxyService}.
 */
@RestController
@RequestMapping("/api/local-ai/models")
public class LocalAiModelController {

    private static final String HUGGING_FACE_SOURCE = "huggingface";

    private final LocalAiCatalogService catalogService;
    private final LocalAiModelDownloadProxyService downloadProxyService;

    public LocalAiModelController(LocalAiCatalogService catalogService,
                                   LocalAiModelDownloadProxyService downloadProxyService) {
        this.catalogService = catalogService;
        this.downloadProxyService = downloadProxyService;
    }

    /** All supported on-device models. */
    @GetMapping
    public ResponseEntity<List<LocalAiModelDto>> list(HttpServletRequest request) {
        return ResponseEntity.ok(catalogService.listModels().stream()
                .map(model -> withPublicDownloadUrl(model, request))
                .toList());
    }

    /** A single catalog entry by id. */
    @GetMapping("/{id}")
    public ResponseEntity<LocalAiModelDto> get(@PathVariable String id, HttpServletRequest request) {
        return catalogService.findModel(id)
                .map(model -> withPublicDownloadUrl(model, request))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown model: " + id));
    }

    /** Streams the model artifact, proxying gated sources with a server-held credential. */
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range
    ) {
        LocalAiModelDto model = catalogService.findModel(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown model: " + id));
        return downloadProxyService.proxy(model, range);
    }

    private LocalAiModelDto withPublicDownloadUrl(LocalAiModelDto model, HttpServletRequest request) {
        if (!HUGGING_FACE_SOURCE.equals(model.source())) {
            return model;
        }
        String proxyUrl = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/api/local-ai/models/" + model.id() + "/download")
                .build()
                .toUriString();
        return new LocalAiModelDto(
                model.id(), model.name(), model.provider(), model.source(), model.version(),
                model.engine(), model.fileName(), model.sizeBytes(), model.sha256(), proxyUrl,
                model.licenseUrl(), model.minimumRamGb(), model.minimumStorageGb(), model.recommended());
    }
}
