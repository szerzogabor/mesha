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
    void qwen25HalfBillionUsesMediaPipeEngine() {
        LocalAiCatalogService service = serviceWith(new LocalAiCatalogProperties());

        LocalAiModelDto model = service.findModel("qwen2.5-0.5b").orElseThrow();

        assertThat(model.engine()).isEqualTo("mediapipe");
        assertThat(model.fileName()).isEqualTo("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task");
        assertThat(model.sizeBytes()).isPositive();
        assertThat(model.downloadUrl()).startsWith("https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/");
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
}
