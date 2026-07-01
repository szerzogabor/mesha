package com.mesha.api.service;

import com.mesha.api.config.LocalAiCatalogProperties;
import com.mesha.api.dto.LocalAiModelDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiCatalogServiceTest {

    private LocalAiCatalogService serviceWith(LocalAiCatalogProperties props) {
        return new LocalAiCatalogService(props);
    }

    @Test
    void servesBuiltInCatalogByDefault() {
        LocalAiCatalogService service = serviceWith(new LocalAiCatalogProperties());

        List<LocalAiModelDto> models = service.listModels();

        assertThat(models).isNotEmpty();
        assertThat(models).anyMatch(m -> m.id().equals("gemma-4-e2b"));
        assertThat(models).anyMatch(m -> m.id().equals("gemma-3n-e2b"));
    }

    @Test
    void gemma4E2BUsesLiteRtLmEngine() {
        LocalAiCatalogService service = serviceWith(new LocalAiCatalogProperties());

        LocalAiModelDto model = service.findModel("gemma-4-e2b").orElseThrow();

        assertThat(model.engine()).isEqualTo("litertlm");
        assertThat(model.fileName()).isEqualTo("gemma-4-E2B-it.litertlm");
        assertThat(model.recommended()).isTrue();
        assertThat(model.sizeBytes()).isPositive();
        assertThat(model.downloadUrl()).startsWith("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/");
    }

    @Test
    void listsRecommendedModelsFirst() {
        LocalAiCatalogService service = serviceWith(new LocalAiCatalogProperties());

        List<LocalAiModelDto> models = service.listModels();

        // The first entry must be a recommended model.
        assertThat(models.get(0).recommended()).isTrue();
    }

    @Test
    void findModelReturnsMatchingEntry() {
        LocalAiCatalogService service = serviceWith(new LocalAiCatalogProperties());

        assertThat(service.findModel("gemma-3n-e2b")).isPresent();
        assertThat(service.findModel("does-not-exist")).isEmpty();
    }

    @Test
    void configuredModelOverridesBuiltInWithSameId() {
        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setModels(List.of(new LocalAiModelDto(
                "gemma-3n-e2b", "Custom Gemma", "Google", "mesha-cdn", "2.0", "mediapipe",
                "custom.task", 123L, "deadbeef", "https://cdn.mesha.dev/custom.task",
                null, null, 6, 5, true)));

        LocalAiCatalogService service = serviceWith(props);

        LocalAiModelDto overridden = service.findModel("gemma-3n-e2b").orElseThrow();
        assertThat(overridden.name()).isEqualTo("Custom Gemma");
        assertThat(overridden.version()).isEqualTo("2.0");
        assertThat(overridden.downloadUrl()).isEqualTo("https://cdn.mesha.dev/custom.task");
    }

    @Test
    void includeDefaultsFalseServesOnlyConfiguredModels() {
        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setIncludeDefaults(false);
        props.setModels(List.of(new LocalAiModelDto(
                "qwen-2-5", "Qwen 2.5", "Alibaba", "huggingface", "1.0", "llama.cpp",
                "qwen.gguf", 456L, "", "https://example.com/qwen.gguf",
                null, null, 4, 3, false)));

        LocalAiCatalogService service = serviceWith(props);

        List<LocalAiModelDto> models = service.listModels();
        assertThat(models).hasSize(1);
        assertThat(models.get(0).id()).isEqualTo("qwen-2-5");
    }

    @Test
    void isEngineSupported_trueForEnginesTheMobileAppImplements() {
        assertThat(LocalAiCatalogService.isEngineSupported("mediapipe")).isTrue();
        assertThat(LocalAiCatalogService.isEngineSupported("litertlm")).isTrue();
    }

    @Test
    void isEngineSupported_falseForEngineWithNoClientProviderYet() {
        // llama.cpp is the documented example of a "not implemented yet" engine (see
        // includeDefaultsFalseServesOnlyConfiguredModels): the catalog stays engine-agnostic
        // and still serves it, but the app has nothing to run it with today.
        assertThat(LocalAiCatalogService.isEngineSupported("llama.cpp")).isFalse();
    }

    @Test
    void modelWithUnsupportedEngineIsStillServed() {
        // Serving stays unblocked even for an unsupported engine — see
        // includeDefaultsFalseServesOnlyConfiguredModels for the equivalent assertion; this
        // only re-confirms it alongside isEngineSupported() so the two don't drift apart.
        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setModels(List.of(new LocalAiModelDto(
                "qwen-2-5", "Qwen 2.5", "Alibaba", "huggingface", "1.0", "llama.cpp",
                "qwen.gguf", 456L, "", "https://example.com/qwen.gguf",
                null, null, 4, 3, false)));

        LocalAiCatalogService service = serviceWith(props);

        assertThat(service.findModel("qwen-2-5")).isPresent();
    }
}
