package com.mesha.api.controller;

import com.mesha.api.dto.LocalAiModelDto;
import com.mesha.api.service.LocalAiCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Local AI model catalog endpoints.
 *
 * <p>Read-only and public (registered as {@code permitAll()} in {@code SecurityConfig}):
 * the catalog is non-sensitive reference data the mobile app needs to discover, download
 * and manage on-device models. The backend is the source of truth — clients must never
 * hardcode model download URLs.
 */
@RestController
@RequestMapping("/api/local-ai/models")
public class LocalAiModelController {

    private final LocalAiCatalogService catalogService;

    public LocalAiModelController(LocalAiCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** All supported on-device models. */
    @GetMapping
    public ResponseEntity<List<LocalAiModelDto>> list() {
        return ResponseEntity.ok(catalogService.listModels());
    }

    /** A single catalog entry by id. */
    @GetMapping("/{id}")
    public ResponseEntity<LocalAiModelDto> get(@PathVariable String id) {
        return catalogService.findModel(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown model: " + id));
    }
}
