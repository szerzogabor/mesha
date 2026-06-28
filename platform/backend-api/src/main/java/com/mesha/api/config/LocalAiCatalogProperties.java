package com.mesha.api.config;

import com.mesha.api.dto.LocalAiModelDto;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Externalised configuration for the Local AI model catalog.
 *
 * <p>The {@code LocalAiCatalogService} ships a curated built-in catalog; these properties
 * let an operator extend or override it (e.g. point at a Mesha CDN mirror, add a new model,
 * or disable the defaults entirely) without a code change.
 *
 * <pre>
 * mesha:
 *   local-ai:
 *     include-defaults: true
 *     models:
 *       - id: my-model
 *         name: My Model
 *         engine: mediapipe
 *         download-url: https://example.com/model.task
 *         ...
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "mesha.local-ai")
public class LocalAiCatalogProperties {

    /** When true, the built-in curated catalog is served alongside any configured models. */
    private boolean includeDefaults = true;

    /** Operator-supplied models, merged with (and overriding by id) the built-in defaults. */
    private List<LocalAiModelDto> models = new ArrayList<>();

    /**
     * Service-account Hugging Face access token used to fetch gated model artifacts (e.g.
     * the Gemma 3n litert-preview repos) on the user's behalf via the download proxy, so the
     * mobile client never needs its own Hugging Face credentials. Blank disables the
     * Authorization header — fine for ungated sources, a 401 for gated ones.
     */
    private String huggingFaceToken = "";

    public boolean isIncludeDefaults() {
        return includeDefaults;
    }

    public void setIncludeDefaults(boolean includeDefaults) {
        this.includeDefaults = includeDefaults;
    }

    public List<LocalAiModelDto> getModels() {
        return models;
    }

    public void setModels(List<LocalAiModelDto> models) {
        this.models = models;
    }

    public String getHuggingFaceToken() {
        return huggingFaceToken;
    }

    public void setHuggingFaceToken(String huggingFaceToken) {
        this.huggingFaceToken = huggingFaceToken;
    }
}
