package com.mesha.api.service;

import com.mesha.api.config.LocalAiCatalogProperties;
import com.mesha.api.dto.LocalAiModelDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Source of truth for the Local AI model catalog the mobile app downloads from.
 *
 * <p>Serves a curated built-in catalog of supported on-device models, optionally extended
 * or overridden via {@link LocalAiCatalogProperties}. Operator-configured models with the
 * same {@code id} as a built-in replace it, so a deployment can repoint a model at a CDN
 * mirror or correct a checksum without a code change. The catalog is engine- and
 * provider-agnostic: entries simply describe a downloadable artifact plus the metadata the
 * client needs to verify, store and (later) run it.
 */
@Service
public class LocalAiCatalogService {

    private final LocalAiCatalogProperties properties;

    public LocalAiCatalogService(LocalAiCatalogProperties properties) {
        this.properties = properties;
    }

    /** All supported models, recommended ones first then by display name. */
    public List<LocalAiModelDto> listModels() {
        Map<String, LocalAiModelDto> byId = new LinkedHashMap<>();
        if (properties.isIncludeDefaults()) {
            for (LocalAiModelDto model : defaultCatalog()) {
                byId.put(model.id(), model);
            }
        }
        // Configured models override built-ins sharing the same id.
        for (LocalAiModelDto model : properties.getModels()) {
            if (model.id() != null && !model.id().isBlank()) {
                byId.put(model.id(), model);
            }
        }
        return byId.values().stream()
                .sorted((a, b) -> {
                    if (a.recommended() != b.recommended()) {
                        return a.recommended() ? -1 : 1;
                    }
                    return String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name()));
                })
                .toList();
    }

    /** A single catalog entry by id, or empty if unknown. */
    public Optional<LocalAiModelDto> findModel(String id) {
        return listModels().stream().filter(m -> m.id().equals(id)).findFirst();
    }

    /**
     * Built-in curated catalog. These are the on-device models Mesha officially supports.
     * Checksums are intentionally left blank where an authoritative value is not yet pinned;
     * the client verifies SHA-256 whenever a non-blank value is supplied here.
     */
    private List<LocalAiModelDto> defaultCatalog() {
        return List.of(
                new LocalAiModelDto(
                        "gemma-4-e2b",
                        "Gemma 4 E2B",
                        "Google",
                        "huggingface",
                        "1.0",
                        "litertlm",
                        "gemma-4-E2B-it.litertlm",
                        2_771_918_438L,
                        "",
                        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                        null,
                        "https://ai.google.dev/gemma/terms",
                        4,
                        4,
                        true
                ),
                new LocalAiModelDto(
                        "gemma-3n-e2b",
                        "Gemma 3n E2B",
                        "Google",
                        "huggingface",
                        "1.0",
                        "mediapipe",
                        "gemma-3n-E2B-it-int4.task",
                        3_136_226_711L,
                        "",
                        "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
                        null,
                        "https://ai.google.dev/gemma/terms",
                        6,
                        5,
                        false
                ),
                new LocalAiModelDto(
                        "gemma-3n-e4b",
                        "Gemma 3n E4B",
                        "Google",
                        "huggingface",
                        "1.0",
                        "mediapipe",
                        "gemma-3n-E4B-it-int4.task",
                        4_405_655_031L,
                        "",
                        "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task",
                        null,
                        "https://ai.google.dev/gemma/terms",
                        8,
                        6,
                        false
                )
        );
    }
}
