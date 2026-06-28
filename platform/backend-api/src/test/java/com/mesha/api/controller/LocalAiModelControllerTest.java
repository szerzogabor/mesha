package com.mesha.api.controller;

import com.mesha.api.config.LocalAiCatalogProperties;
import com.mesha.api.dto.LocalAiModelDto;
import com.mesha.api.service.LocalAiCatalogService;
import com.mesha.api.service.LocalAiModelDownloadProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAiModelControllerTest {

    private HttpServletRequest requestTo(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("api.mesha.dev");
        when(request.getServerPort()).thenReturn(443);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void rewritesDownloadUrlToProxyEndpointForHuggingFaceModels() {
        LocalAiModelController controller = new LocalAiModelController(
                new LocalAiCatalogService(new LocalAiCatalogProperties()),
                new LocalAiModelDownloadProxyService(new LocalAiCatalogProperties()));

        LocalAiModelDto model = controller.get("gemma-3n-e2b", requestTo("/api/local-ai/models/gemma-3n-e2b")).getBody();

        assertThat(model.downloadUrl()).isEqualTo("https://api.mesha.dev/api/local-ai/models/gemma-3n-e2b/download");
    }

    @Test
    void leavesDownloadUrlUntouchedForNonHuggingFaceModels() {
        LocalAiCatalogProperties props = new LocalAiCatalogProperties();
        props.setModels(java.util.List.of(new LocalAiModelDto(
                "custom-model", "Custom", "Mesha", "mesha-cdn", "1.0", "mediapipe",
                "custom.task", 123L, "", "https://cdn.mesha.dev/custom.task",
                null, 4, 3, false)));
        LocalAiCatalogService catalogService = new LocalAiCatalogService(props);
        LocalAiModelController controller = new LocalAiModelController(
                catalogService, new LocalAiModelDownloadProxyService(props));

        LocalAiModelDto model = controller.get("custom-model", requestTo("/api/local-ai/models/custom-model")).getBody();

        assertThat(model.downloadUrl()).isEqualTo("https://cdn.mesha.dev/custom.task");
    }
}
